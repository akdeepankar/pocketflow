import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const RUNWAY_BASE = "https://api.dev.runwayml.com/v1";
const RUNWAY_VERSION = "2024-11-06";
const ONESIGNAL_APP_ID = "7090ae90-1a87-4702-8cfd-2694e44301d9";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (req) => {
  console.log(`[Runway] >>> Function invoked: ${req.method} at ${new Date().toISOString()}`);

  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const apiKey = Deno.env.get("RUNWAY_API_KEY");
  if (!apiKey) {
    console.error("[Runway] ❌ ERROR: RUNWAY_API_KEY environment variable is not set");
    return new Response(
      JSON.stringify({ success: false, error: "Server configuration error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  const oneSignalRestKey = Deno.env.get("ONESIGNAL_REST_API_KEY") || "";
  console.log(`[Runway] Config status: RUNWAY_API_KEY=OK, ONESIGNAL_REST_API_KEY=${oneSignalRestKey ? "OK" : "MISSING"}`);

  let rawText = "";
  try {
    rawText = await req.text();
  } catch (e) {
    console.error("[Runway] ❌ Failed to read request text:", e);
  }
  console.log(`[Runway] Raw body received (${rawText.length} bytes):`, rawText.substring(0, 300));

  let body: any;
  try {
    body = JSON.parse(rawText);
    if (typeof body === "string") {
      body = JSON.parse(body);
    }
  } catch (e) {
    console.error("[Runway] ❌ Invalid JSON:", e);
    return new Response(
      JSON.stringify({ success: false, error: "Invalid JSON request body" }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  const { endpoint, payload, liveActivity, recipientUserId } = body || {};
  console.log(`[Runway] Parsed parameters: endpoint=${endpoint}, hasPayload=${!!payload}, hasLiveActivity=${!!liveActivity}, activityId=${liveActivity?.activityId}`);

  if (!endpoint || !payload) {
    console.error(`[Runway] ❌ Missing 'endpoint' or 'payload'. endpoint=${endpoint}, payload=${JSON.stringify(payload)}`);
    return new Response(
      JSON.stringify({ success: false, error: "Missing 'endpoint' or 'payload' in request" }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  console.log(`[Runway] Calling API endpoint: ${endpoint}`);

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
      console.error(`[Runway] API error ${response.status}: ${JSON.stringify(data)}`);
      return new Response(
        JSON.stringify({ success: false, error: `Runway ${response.status}: ${JSON.stringify(data)}` }),
        { status: response.status, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const taskId = data.id;
    console.log(`[Runway] Task created successfully: ${taskId}`);

    // ── Background Poller via EdgeRuntime.waitUntil ──────────────────────
    if (taskId && (liveActivity || recipientUserId)) {
      console.log(`[Background Poller] Registering background task via EdgeRuntime.waitUntil for task: ${taskId}`);

      EdgeRuntime.waitUntil((async () => {
        console.log(`[Background Poller] Starting background poll loop for task ${taskId}...`);
        const maxAttempts = 180; // 15 mins max (5s intervals)
        let status = "PENDING";
        let outputUrl = null;
        let errorMessage = null;

        const nodeTitle = liveActivity?.nodeTitle || "Generation";
        const workflowName = liveActivity?.workflowName || "Workflow";
        const totalSteps = liveActivity?.totalSteps || 1;
        const currentStep = liveActivity?.currentStep || 1;
        const stepNodeTypes = liveActivity?.stepNodeTypes || [];
        const nodeType = liveActivity?.nodeType || "IMAGE_GENERATION";

        for (let attempt = 0; attempt < maxAttempts; attempt++) {
          await new Promise((resolve) => setTimeout(resolve, 5000));
          const elapsedSeconds = (attempt + 1) * 5;

          try {
            const pollRes = await fetch(`${RUNWAY_BASE}/tasks/${taskId}`, {
              headers: {
                "Authorization": `Bearer ${apiKey}`,
                "X-Runway-Version": RUNWAY_VERSION,
              },
            });
            if (!pollRes.ok) {
              console.warn(`[Background Poller] Poll returned HTTP ${pollRes.status}`);
              continue;
            }
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
            } else if (liveActivity?.activityId && oneSignalRestKey && attempt > 0 && attempt % 2 === 0) {
              // Send periodic progress update every 10 seconds while app is in background/killed
              const estimatedProgress = Math.min(0.15 + (attempt * 0.05), 0.90);
              const progressText = totalSteps > 1
                ? `Step ${currentStep}/${totalSteps}: Generating ${nodeTitle} (${elapsedSeconds}s)...`
                : `Generating ${nodeTitle} (${elapsedSeconds}s)...`;

              console.log(`[OneSignal Progress] Sending progress update (${elapsedSeconds}s, ~${Math.round(estimatedProgress * 100)}%)...`);
              fetch(
                `https://api.onesignal.com/apps/${ONESIGNAL_APP_ID}/live_activities/${liveActivity.activityId}/notifications`,
                {
                  method: "POST",
                  headers: {
                    "Authorization": `Key ${oneSignalRestKey}`,
                    "Content-Type": "application/json",
                  },
                  body: JSON.stringify({
                    name: "PocketFlow Progress Update",
                    event: "update",
                    priority: 10,
                    contents: { en: progressText },
                    event_updates: {
                      status: progressText,
                      nodeTitle: nodeTitle,
                      workflowName: workflowName,
                      currentStep: currentStep,
                      totalSteps: totalSteps,
                      completedSteps: currentStep - 1,
                      stepNodeTypes: stepNodeTypes,
                      currentNodeType: nodeType,
                      progress: estimatedProgress,
                      isFinished: false,
                      isSuccess: false,
                      timestamp: Math.floor(Date.now() / 1000),
                    },
                  }),
                }
              ).catch((e) => console.error("[OneSignal Progress] Push error:", e));
            }
          } catch (err) {
            console.error(`[Background Poller] Network error polling task ${taskId}:`, err);
          }
        }

        const isSuccess = status === "SUCCEEDED";

        // 1. Send OneSignal Live Activity Remote End Event
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
              dismissal_date: nowUnix + 5, // Dismiss from lock screen 5s after completion
              priority: 10,
              contents: {
                en: finalStatusText,
              },
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

    return new Response(
      JSON.stringify({ success: true, jobId: taskId, data }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );

  } catch (err) {
    console.error(`Exception calling Runway: ${err.message}`);
    return new Response(
      JSON.stringify({ success: false, error: err.message }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
