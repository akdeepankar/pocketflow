import "@supabase/functions-js/edge-runtime.d.ts";

const RUNWAY_BASE = "https://api.dev.runwayml.com/v1";
const RUNWAY_VERSION = "2024-11-06";
const ONESIGNAL_APP_ID = "7090ae90-1a87-4702-8cfd-2694e44301d9";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

// Track active job per Live Activity ID across poller instances
const activeJobs = new Map<string, string>();

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const apiKey = Deno.env.get("RUNWAY_API_KEY") || "";
  const oneSignalRestKey = Deno.env.get("ONESIGNAL_REST_API_KEY") || "";

  let body: Record<string, any>;
  try {
    const rawText = await req.text();
    let parsed = JSON.parse(rawText);
    while (typeof parsed === "string") {
      try {
        parsed = JSON.parse(parsed);
      } catch {
        break;
      }
    }
    body = (parsed && typeof parsed === "object") ? parsed : {};
  } catch (err) {
    return new Response(JSON.stringify({ success: false, error: "Invalid JSON body" }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const { jobId, provider = "runway", liveActivity, recipientUserId } = body;

  if (!jobId) {
    return new Response(JSON.stringify({ success: false, error: "Missing 'jobId' in request body" }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  console.log(`[poll-job] 🎯 Received job for Live Activity polling: jobId=${jobId}, provider=${provider}`);

  // Register this job as the active job for this Live Activity ID
  if (liveActivity?.activityId) {
    activeJobs.set(liveActivity.activityId, jobId);
  }

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
  const runPoller = async () => {
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

    // Send an immediate first update so OneSignal records the activity right away
    if (activityId) {
      const initialStatusText = totalSteps > 1
        ? `Step ${currentStep}/${totalSteps}: Generating ${nodeTitle}...`
        : `Generating ${nodeTitle}...`;
      lastSentStatus = initialStatusText;
      await sendLiveActivityEvent(activityId, "update", {
        status: initialStatusText,
        nodeTitle,
        workflowName,
        currentStep,
        totalSteps,
        completedSteps: Math.max(0, currentStep - 1),
        stepNodeTypes,
        currentNodeType: nodeType,
        progress: -1.0,
        isFinished: false,
        isSuccess: false,
        timestamp: Date.now() / 1000,
      });
    }

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
    const isLastStep = currentStep >= totalSteps;
    let takenOver = false;

    // 1. Send OneSignal Live Activity Update or End Event
    if (activityId) {
      if (isSuccess && !isLastStep) {
        // Intermediate step succeeded — update status
        const stepDoneText = totalSteps > 1
          ? `Step ${currentStep}/${totalSteps}: ${nodeTitle} Completed ✓`
          : `${nodeTitle} Completed! ✓`;

        await sendLiveActivityEvent(activityId, "update", {
          status: stepDoneText,
          nodeTitle,
          workflowName,
          currentStep,
          totalSteps,
          completedSteps: currentStep,
          stepNodeTypes,
          currentNodeType: nodeType,
          progress: currentStep / totalSteps,
          isFinished: false,
          isSuccess: true,
          timestamp: Date.now() / 1000,
        });

        // Wait up to 20 seconds to see if the next step's poller takes over.
        // If the app was closed/killed, no next step will start.
        console.log(`[poll-job] ⏳ Waiting up to 20s for next step takeoff on activityId=${activityId}...`);
        for (let check = 0; check < 20; check++) {
          await new Promise((resolve) => setTimeout(resolve, 1000));
          if (activeJobs.get(activityId) !== jobId) {
            takenOver = true;
            console.log(`[poll-job] ⏩ Next step took over for activityId=${activityId}. Exiting previous poller.`);
            break;
          }
        }

        if (!takenOver) {
          // App was closed — no next step started within 20s.
          // Cleanly end the Live Activity so it doesn't freeze on the lock screen.
          console.log(`[poll-job] ⏱️ No next step started within 20s for activityId=${activityId} (App closed). Ending Live Activity.`);
          await sendLiveActivityEvent(activityId, "end", {
            status: `Step ${currentStep}/${totalSteps} Completed ✓`,
            nodeTitle,
            workflowName,
            currentStep,
            totalSteps,
            completedSteps: currentStep,
            stepNodeTypes,
            currentNodeType: nodeType,
            progress: currentStep / totalSteps,
            isFinished: true,
            isSuccess: true,
            timestamp: Date.now() / 1000,
          });
        }
      } else {
        // Final step succeeded OR any step failed — end the activity immediately
        const finalStatusText = isSuccess
          ? (totalSteps > 1 ? `All ${totalSteps} nodes completed! ✓` : `${nodeTitle} Completed! ✓`)
          : (errorMessage || "Generation Failed");

        await sendLiveActivityEvent(activityId, "end", {
          status: finalStatusText,
          nodeTitle,
          workflowName,
          currentStep: isSuccess ? totalSteps : currentStep,
          totalSteps,
          completedSteps: isSuccess ? totalSteps : Math.max(0, currentStep - 1),
          stepNodeTypes,
          currentNodeType: nodeType,
          progress: isSuccess ? 1.0 : currentStep / totalSteps,
          isFinished: true,
          isSuccess,
          timestamp: Date.now() / 1000,
        });
      }
    }

    // 2. Send Push Notification to target user (on final step, failure, or when app closed mid-workflow)
    if ((isLastStep || !isSuccess || !takenOver) && recipientUserId && recipientUserId.trim().length > 0 && oneSignalRestKey) {
      try {
        console.log(`[poll-job] 🔔 Sending push notification to user: ${recipientUserId}`);
        const pushHeading = isSuccess
          ? (isLastStep ? `Workflow Complete (${workflowName})` : `Step ${currentStep}/${totalSteps} Complete (${workflowName})`)
          : `Generation Failed (${workflowName})`;
        const pushContent = isSuccess
          ? (isLastStep ? `All ${totalSteps} nodes finished generating!` : `${nodeTitle} finished generating! (App closed)`)
          : `${nodeTitle} failed to generate.`;

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
              en: pushHeading,
            },
            contents: {
              en: pushContent,
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
  };

  // Launch background poller via EdgeRuntime.waitUntil
  if (typeof (globalThis as any).EdgeRuntime?.waitUntil === "function") {
    (globalThis as any).EdgeRuntime.waitUntil(runPoller());
  } else {
    runPoller();
  }

  return new Response(JSON.stringify({ success: true, message: "Live activity polling started in background" }), {
    status: 200,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
});
