import "@supabase/functions-js/edge-runtime.d.ts";
import { withSupabase } from "@supabase/server";

const RUNWAY_BASE = "https://api.dev.runwayml.com/v1";
const RUNWAY_VERSION = "2024-11-06";
const ONESIGNAL_APP_ID = "7090ae90-1a87-4702-8cfd-2694e44301d9";

export default {
  fetch: withSupabase({ auth: ["publishable", "secret"] }, async (req, ctx) => {
    const apiKey = Deno.env.get("RUNWAY_API_KEY") || "";
    const oneSignalRestKey = Deno.env.get("ONESIGNAL_REST_API_KEY") || "";

    let body;
    try {
      body = await req.json();
    } catch {
      return Response.json({ success: false, error: "Invalid JSON body" }, { status: 400 });
    }

    const { jobId, provider = "runway", liveActivity, recipientUserId } = body;

    if (!jobId) {
      return Response.json({ success: false, error: "Missing 'jobId' in request body" }, { status: 400 });
    }

    console.log(`[poll-job] 🎯 Received job for Live Activity polling: jobId=${jobId}, provider=${provider}`);

    // Run polling and OneSignal Live Activity updates asynchronously in background
    ctx.waitUntil((async () => {
      console.log(`[poll-job] 🚀 Starting Live Activity poller for task: ${jobId}`);
      const maxAttempts = 180; // 15 mins maximum
      let status = "PENDING";
      let outputUrl: string | null = null;
      let errorMessage: string | null = null;
      let lastProgressRatio = -1;

      const nodeTitle = liveActivity?.nodeTitle || "Generation";
      const workflowName = liveActivity?.workflowName || "Workflow";
      const totalSteps = liveActivity?.totalSteps || 1;
      const currentStep = liveActivity?.currentStep || 1;
      const stepNodeTypes = liveActivity?.stepNodeTypes || [];
      const nodeType = liveActivity?.nodeType || "VIDEO_GENERATION";
      const activityId = liveActivity?.activityId;

      for (let attempt = 0; attempt < maxAttempts; attempt++) {
        await new Promise((resolve) => setTimeout(resolve, 5000));

        try {
          if (provider === "runway") {
            const pollRes = await fetch(`${RUNWAY_BASE}/tasks/${jobId}`, {
              headers: {
                "Authorization": `Bearer ${apiKey}`,
                "X-Runway-Version": RUNWAY_VERSION,
              },
            });

            if (!pollRes.ok) {
              console.warn(`[poll-job] Runway poll HTTP ${pollRes.status} for task ${jobId}`);
              continue;
            }

            const pollData = await pollRes.json();
            status = pollData.status;
            const progressRatio = typeof pollData.progressRatio === "number" ? pollData.progressRatio : -1;

            // Send intermediate OneSignal Live Activity update if progress advanced
            if (activityId && oneSignalRestKey && (status === "RUNNING" || status === "PENDING")) {
              if (progressRatio > 0 && Math.abs(progressRatio - lastProgressRatio) >= 0.1) {
                lastProgressRatio = progressRatio;
                const percent = Math.round(progressRatio * 100);
                const progressStatusText = totalSteps > 1
                  ? `Step ${currentStep}/${totalSteps}: Generating ${nodeTitle} (${percent}%)...`
                  : `Generating ${nodeTitle} (${percent}%)...`;

                try {
                  console.log(`[poll-job] 📡 Sending Live Activity progress update (${percent}%) for ${activityId}`);
                  await fetch(
                    `https://onesignal.com/api/v1/apps/${ONESIGNAL_APP_ID}/live_activities/${activityId}/notifications`,
                    {
                      method: "POST",
                      headers: {
                        "Authorization": `Key ${oneSignalRestKey}`,
                        "Content-Type": "application/json",
                      },
                      body: JSON.stringify({
                        name: "PocketFlow Live Activity Progress Update",
                        event: "update",
                        event_updates: {
                          status: progressStatusText,
                          nodeTitle,
                          workflowName,
                          currentStep,
                          totalSteps,
                          completedSteps: currentStep - 1,
                          stepNodeTypes,
                          currentNodeType: nodeType,
                          progress: progressRatio,
                          isFinished: false,
                          isSuccess: false,
                          timestamp: Math.floor(Date.now() / 1000),
                        },
                      }),
                    }
                  );
                } catch (updateErr) {
                  console.error(`[poll-job] Failed to send Live Activity progress update:`, updateErr);
                }
              }
            }

            if (status === "SUCCEEDED") {
              outputUrl = pollData.output?.[0] || pollData.artifactUrl || null;
              console.log(`[poll-job] ✅ Task ${jobId} SUCCEEDED! outputUrl=${outputUrl}`);
              break;
            } else if (status === "FAILED") {
              errorMessage = pollData.failure || pollData.failureCode || "Task failed";
              console.error(`[poll-job] ❌ Task ${jobId} FAILED: ${errorMessage}`);
              break;
            }
          }
        } catch (pollErr) {
          console.error(`[poll-job] Error polling task ${jobId}:`, pollErr);
        }
      }

      const isSuccess = status === "SUCCEEDED";

      // 1. Send OneSignal Live Activity Remote End Event
      if (activityId && oneSignalRestKey) {
        const finalStatusText = isSuccess
          ? (totalSteps > 1 ? `All ${totalSteps} nodes completed! ✓` : `${nodeTitle} Completed! ✓`)
          : (errorMessage || "Generation Failed");

        const liveActivityPayload = {
          name: "PocketFlow Live Activity Completion",
          event: "end",
          event_updates: {
            status: finalStatusText,
            nodeTitle,
            workflowName,
            currentStep: totalSteps,
            totalSteps,
            completedSteps: isSuccess ? totalSteps : Math.max(0, currentStep - 1),
            stepNodeTypes,
            currentNodeType: nodeType,
            progress: 1.0,
            isFinished: true,
            isSuccess,
            timestamp: Math.floor(Date.now() / 1000),
          },
        };

        try {
          console.log(`[poll-job] 🏁 Sending Live Activity END event for activity: ${activityId}`);
          const laRes = await fetch(
            `https://onesignal.com/api/v1/apps/${ONESIGNAL_APP_ID}/live_activities/${activityId}/notifications`,
            {
              method: "POST",
              headers: {
                "Authorization": `Key ${oneSignalRestKey}`,
                "Content-Type": "application/json",
              },
              body: JSON.stringify(liveActivityPayload),
            }
          );
          console.log(`[poll-job] OneSignal Live Activity end status: ${laRes.status}`);
        } catch (laErr) {
          console.error(`[poll-job] Failed to send Live Activity end notification:`, laErr);
        }
      }

      // 2. Send Push Notification to target user
      if (recipientUserId && recipientUserId.trim().length > 0 && oneSignalRestKey) {
        try {
          console.log(`[poll-job] 🔔 Sending push notification to user: ${recipientUserId}`);
          await fetch("https://onesignal.com/api/v1/notifications", {
            method: "POST",
            headers: {
              "Authorization": `Key ${oneSignalRestKey}`,
              "Content-Type": "application/json",
            },
            body: JSON.stringify({
              app_id: ONESIGNAL_APP_ID,
              target_channel: "push",
              include_aliases: {
                external_id: [recipientUserId.trim()],
              },
              headings: {
                en: isSuccess ? `Generation Complete (${workflowName})` : `Generation Failed (${workflowName})`,
              },
              contents: {
                en: isSuccess ? `${nodeTitle} finished generating!` : `${nodeTitle} failed to generate.`,
              },
              data: {
                workflow_id: liveActivity?.workflowId || "",
                node_id: activityId || "",
                type: "generation_complete",
                output_url: outputUrl || "",
              },
            }),
          });
        } catch (pushErr) {
          console.error(`[poll-job] Error sending push notification:`, pushErr);
        }
      }
    })());

    return Response.json({ success: true, message: "Live activity polling started in background" });
  }),
};
