import "@supabase/functions-js/edge-runtime.d.ts";
import { withSupabase } from "@supabase/server";

const RUNWAY_BASE = "https://api.dev.runwayml.com/v1";
const RUNWAY_VERSION = "2024-11-06";
const ONESIGNAL_APP_ID = "7090ae90-1a87-4702-8cfd-2694e44301d9";

export default {
  fetch: withSupabase({ auth: ["publishable", "secret"] }, async (req, ctx) => {
    const apiKey = Deno.env.get("RUNWAY_API_KEY");
    if (!apiKey) {
      console.error("RUNWAY_API_KEY environment variable is not set");
      return Response.json({ success: false, error: "Server configuration error" }, { status: 500 });
    }

    const oneSignalRestKey = Deno.env.get("ONESIGNAL_REST_API_KEY") || "";

    let body;
    try {
      body = await req.json();
    } catch (e) {
      return Response.json({ success: false, error: "Invalid request body" }, { status: 400 });
    }

    const { endpoint, payload, liveActivity, recipientUserId } = body;

    if (!endpoint || !payload) {
      return Response.json({ success: false, error: "Missing 'endpoint' or 'payload' in request" }, { status: 400 });
    }

    console.log(`Calling Runway API: ${endpoint}`);

    try {
      const response = await fetch(`${RUNWAY_BASE}${endpoint}`, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${apiKey}`,
          "X-Runway-Version": RUNWAY_VERSION,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
      });

      const data = await response.json();

      if (!response.ok) {
        console.error(`Runway API error ${response.status}: ${JSON.stringify(data)}`);
        return Response.json(
          { success: false, error: `Runway ${response.status}: ${JSON.stringify(data)}` },
          { status: response.status }
        );
      }

      const taskId = data.id;
      console.log(`Runway task created: ${taskId}`);

      // ── Background Poller via ctx.waitUntil ──────────────────────────────
      if (taskId && (liveActivity || recipientUserId)) {
        ctx.waitUntil((async () => {
          console.log(`[Background Poller] Starting background poll for Runway task: ${taskId}`);
          const maxAttempts = 180; // 15 mins max
          let status = "PENDING";
          let outputUrl = null;
          let errorMessage = null;

          for (let attempt = 0; attempt < maxAttempts; attempt++) {
            await new Promise((resolve) => setTimeout(resolve, 5000));
            try {
              const pollRes = await fetch(`${RUNWAY_BASE}/tasks/${taskId}`, {
                headers: {
                  "Authorization": `Bearer ${apiKey}`,
                  "X-Runway-Version": RUNWAY_VERSION,
                },
              });
              if (!pollRes.ok) continue;
              const pollData = await pollRes.json();
              status = pollData.status;

              if (status === "SUCCEEDED") {
                outputUrl = pollData.output?.[0] || pollData.artifactUrl || null;
                console.log(`[Background Poller] Task ${taskId} SUCCEEDED! outputUrl=${outputUrl}`);
                break;
              } else if (status === "FAILED") {
                errorMessage = pollData.failure || pollData.failureCode || "Task failed";
                console.error(`[Background Poller] Task ${taskId} FAILED: ${errorMessage}`);
                break;
              }
            } catch (err) {
              console.error(`[Background Poller] Network error polling task ${taskId}:`, err);
            }
          }

          const isSuccess = status === "SUCCEEDED";
          const nodeTitle = liveActivity?.nodeTitle || "Generation";
          const workflowName = liveActivity?.workflowName || "Workflow";
          const totalSteps = liveActivity?.totalSteps || 1;
          const currentStep = liveActivity?.currentStep || 1;
          const stepNodeTypes = liveActivity?.stepNodeTypes || [];
          const nodeType = liveActivity?.nodeType || "IMAGE_GENERATION";

          // 1. Send OneSignal Live Activity Remote Update/End
          if (liveActivity?.activityId) {
            if (!oneSignalRestKey) {
              console.warn("[OneSignal LiveActivity] ⚠️ ONESIGNAL_REST_API_KEY is not set in Supabase Secrets. Cannot send remote Live Activity push.");
            } else {
              const finalStatusText = isSuccess
                ? (totalSteps > 1 ? `All ${totalSteps} nodes completed! ✓` : `${nodeTitle} Completed! ✓`)
                : (errorMessage || "Generation Failed");

              const nowUnix = Math.floor(Date.now() / 1000);
              const liveActivityPayload = {
                name: "PocketFlow Live Activity Completion",
                event: "end",
                dismiss_at: nowUnix + 5, // Dismiss from lock screen 5s after completion
                event_updates: {
                  status: finalStatusText,
                  nodeTitle: nodeTitle,
                  workflowName: workflowName,
                  currentStep: totalSteps,
                  totalSteps: totalSteps,
                  completedSteps: isSuccess ? totalSteps : (currentStep - 1),
                  stepNodeTypes: stepNodeTypes,
                  currentNodeType: nodeType,
                  progress: 1.0,
                  isFinished: true,
                  isSuccess: isSuccess,
                  timestamp: nowUnix,
                },
              };

              try {
                console.log(`[OneSignal LiveActivity] Sending end event for activity: ${liveActivity.activityId}`);
                const laRes = await fetch(
                  `https://api.onesignal.com/apps/${ONESIGNAL_APP_ID}/live_activities/${liveActivity.activityId}/notifications`,
                  {
                    method: "POST",
                    headers: {
                      "Authorization": `Key ${oneSignalRestKey}`,
                      "Content-Type": "application/json",
                    },
                    body: JSON.stringify(liveActivityPayload),
                  }
                );
                const respText = await laRes.text();
                console.log(`[OneSignal LiveActivity] Status: ${laRes.status}, Response: ${respText}`);
              } catch (laErr) {
                console.error(`[OneSignal LiveActivity] Failed to send update:`, laErr);
              }
            }
          }

          // 2. Send Push Notification to the user if app is closed/backgrounded
          if (recipientUserId && oneSignalRestKey) {
            try {
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
                    external_id: [recipientUserId],
                  },
                  headings: {
                    en: isSuccess ? `Generation Complete (${workflowName})` : `Generation Failed (${workflowName})`,
                  },
                  contents: {
                    en: isSuccess ? `${nodeTitle} finished generating!` : `${nodeTitle} failed to generate.`,
                  },
                  data: {
                    workflow_id: liveActivity?.workflowId || "",
                    node_id: liveActivity?.activityId || "",
                    type: "generation_complete",
                  },
                }),
              });
            } catch (pushErr) {
              console.error(`[OneSignal Push] Error sending user notification:`, pushErr);
            }
          }
        })());
      }

      return Response.json({ success: true, jobId: taskId, data });

    } catch (err) {
      console.error(`Exception calling Runway: ${err.message}`);
      return Response.json({ success: false, error: err.message }, { status: 500 });
    }
  }),
};
