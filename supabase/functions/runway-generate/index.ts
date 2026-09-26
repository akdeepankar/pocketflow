import "@supabase/functions-js/edge-runtime.d.ts";

const RUNWAY_BASE = "https://api.dev.runwayml.com/v1";
const RUNWAY_VERSION = "2024-11-06";
const ONESIGNAL_APP_ID = "7090ae90-1a87-4702-8cfd-2694e44301d9";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (req: Request) => {
  // Handle CORS Preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const apiKey = Deno.env.get("RUNWAY_API_KEY");
  if (!apiKey) {
    console.error("RUNWAY_API_KEY environment variable is not set");
    return new Response(
      JSON.stringify({ success: false, error: "Server configuration error: RUNWAY_API_KEY is missing" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  const oneSignalRestKey = Deno.env.get("ONESIGNAL_REST_API_KEY") || "";

  let body;
  try {
    body = await req.json();
  } catch (e) {
    return new Response(
      JSON.stringify({ success: false, error: "Invalid request body" }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  const { endpoint, payload, liveActivity, recipientUserId } = body;

  if (!endpoint || !payload) {
    return new Response(
      JSON.stringify({ success: false, error: "Missing 'endpoint' or 'payload' in request" }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }

  console.log(`[Runway API] Submitting task: ${endpoint}`);

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
      return new Response(
        JSON.stringify({ success: false, error: `Runway ${response.status}: ${JSON.stringify(data)}` }),
        { status: response.status, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const taskId = data.id;
    console.log(`[Runway API] Task created successfully: ${taskId}`);

    // ── Background Poller via EdgeRuntime.waitUntil ──────────────────────
    if (taskId && (liveActivity || recipientUserId)) {
      const backgroundTask = (async () => {
        console.log(`[Background Poller] Started poll for Runway task: ${taskId}`);
        const maxAttempts = 180; // 15 mins max (180 * 5s)
        let status = "PENDING";
        let outputUrl: string | null = null;
        let errorMessage: string | null = null;

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

        // 1. Send OneSignal Live Activity Remote End Push
        if (liveActivity?.activityId && oneSignalRestKey) {
          const finalStatusText = isSuccess
            ? (totalSteps > 1 ? `All ${totalSteps} nodes completed! ✓` : `${nodeTitle} Completed! ✓`)
            : (errorMessage || "Generation Failed");

          const liveActivityPayload = {
            name: "PocketFlow Live Activity Completion",
            event: "end",
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
              timestamp: Math.floor(Date.now() / 1000),
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
            const laText = await laRes.text();
            console.log(`[OneSignal LiveActivity] Status: ${laRes.status}, Body: ${laText}`);
          } catch (laErr) {
            console.error(`[OneSignal LiveActivity] Failed to send update:`, laErr);
          }
        }

        // 2. Send standard OneSignal Push Notification if recipient user is set
        if (recipientUserId && oneSignalRestKey) {
          try {
            await fetch("https://api.onesignal.com/notifications", {
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
      })();

      if (typeof EdgeRuntime !== "undefined" && typeof EdgeRuntime.waitUntil === "function") {
        EdgeRuntime.waitUntil(backgroundTask);
      }
    }

    return new Response(
      JSON.stringify({ success: true, jobId: taskId, data }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );

  } catch (err: any) {
    console.error(`Exception calling Runway: ${err.message}`);
    return new Response(
      JSON.stringify({ success: false, error: err.message }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
