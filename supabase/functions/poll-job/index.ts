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

    // Helper to send Live Activity remote updates to OneSignal
    const sendLiveActivityEvent = async (
      activityId: string,
      event: "update" | "end",
      eventUpdates: Record<string, unknown>
    ) => {
      if (!activityId || !oneSignalRestKey) return;
      try {
        console.log(`[poll-job] 📡 Sending Live Activity [${event}] for activity: ${activityId}`);
        const res = await fetch(
          `https://onesignal.com/api/v1/apps/${ONESIGNAL_APP_ID}/live_activities/${activityId}/notifications`,
          {
            method: "POST",
            headers: {
              "Authorization": `Key ${oneSignalRestKey}`,
              "Content-Type": "application/json",
            },
            body: JSON.stringify({
              name: `PocketFlow Live Activity ${event === "end" ? "End" : "Update"}`,
              event,
              event_updates: eventUpdates,
            }),
          }
        );
        const resText = await res.text();
        console.log(`[poll-job] OneSignal Live Activity [${event}] response HTTP ${res.status}: ${resText}`);
      } catch (err) {
        console.error(`[poll-job] Failed to send Live Activity [${event}] notification:`, err);
      }
    };

    // Run polling and OneSignal Live Activity updates asynchronously in background
    ctx.waitUntil((async () => {
      console.log(`[poll-job] 🚀 Starting Live Activity poller for task: ${jobId}`);
      const maxAttempts = 180; // ~15 mins maximum (180 * 5s)
      let status = "PENDING";
      let outputUrl: string | null = null;
      let errorMessage: string | null = null;
      let lastSentStatus = "";
      let lastSentProgress = -2;
      let lastSentTime = Date.now();

      const nodeTitle = liveActivity?.nodeTitle || "Generation";
      const workflowName = liveActivity?.workflowName || "Workflow";
      const totalSteps = Number(liveActivity?.totalSteps) || 1;
      const currentStep = Number(liveActivity?.currentStep) || 1;
      const stepNodeTypes: string[] = Array.isArray(liveActivity?.stepNodeTypes) ? liveActivity.stepNodeTypes : [];
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
            const rawProgressRatio = typeof pollData.progressRatio === "number" ? pollData.progressRatio : -1;

            // Determine display status text
            let currentStatusText: string;
            if (status === "RUNNING") {
              if (rawProgressRatio > 0) {
                const percent = Math.round(rawProgressRatio * 100);
                currentStatusText = totalSteps > 1
                  ? `Step ${currentStep}/${totalSteps}: Generating ${nodeTitle} (${percent}%)...`
                  : `Generating ${nodeTitle} (${percent}%)...`;
              } else {
                currentStatusText = totalSteps > 1
                  ? `Step ${currentStep}/${totalSteps}: Generating ${nodeTitle}...`
                  : `Generating ${nodeTitle}...`;
              }
            } else if (status === "PENDING") {
              currentStatusText = totalSteps > 1
                ? `Step ${currentStep}/${totalSteps}: Queued for ${nodeTitle}...`
                : `Queued for ${nodeTitle}...`;
            } else {
              currentStatusText = `${nodeTitle}: ${status}`;
            }

            // Send intermediate OneSignal Live Activity update if status changed,
            // progress advanced by >= 10%, or periodically every 20 seconds
            const now = Date.now();
            const timeSinceLastUpdate = now - lastSentTime;
            const progressDelta = Math.abs(rawProgressRatio - lastSentProgress);
            const shouldSendUpdate =
              activityId &&
              (status === "RUNNING" || status === "PENDING") &&
              (currentStatusText !== lastSentStatus ||
                (rawProgressRatio > 0 && progressDelta >= 0.1) ||
                timeSinceLastUpdate >= 20000);

            if (shouldSendUpdate) {
              lastSentStatus = currentStatusText;
              lastSentProgress = rawProgressRatio;
              lastSentTime = now;

              await sendLiveActivityEvent(activityId, "update", {
                status: currentStatusText,
                nodeTitle,
                workflowName,
                currentStep,
                totalSteps,
                completedSteps: Math.max(0, currentStep - 1),
                stepNodeTypes,
                currentNodeType: nodeType,
                progress: rawProgressRatio,
                isFinished: false,
                isSuccess: false,
                timestamp: Date.now() / 1000,
              });
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
      if (activityId) {
        const finalStatusText = isSuccess
          ? (totalSteps > 1 ? `All ${totalSteps} nodes completed! ✓` : `${nodeTitle} Completed! ✓`)
          : (errorMessage || "Generation Failed");

        await sendLiveActivityEvent(activityId, "end", {
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
          timestamp: Date.now() / 1000,
        });
      }

      // 2. Send Push Notification to target user
      if (recipientUserId && recipientUserId.trim().length > 0 && oneSignalRestKey) {
        try {
          console.log(`[poll-job] 🔔 Sending push notification to user: ${recipientUserId}`);
          const pushRes = await fetch("https://onesignal.com/api/v1/notifications", {
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
          const pushText = await pushRes.text();
          console.log(`[poll-job] Push notification response HTTP ${pushRes.status}: ${pushText}`);
        } catch (pushErr) {
          console.error(`[poll-job] Error sending push notification:`, pushErr);
        }
      }
    })());

    return Response.json({ success: true, message: "Live activity polling started in background" });
  }),
};
