import "@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "@supabase/supabase-js";

const RUNWAY_BASE = "https://api.dev.runwayml.com/v1";
const RUNWAY_VERSION = "2024-11-06";
const ONESIGNAL_APP_ID = "7090ae90-1a87-4702-8cfd-2694e44301d9";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

interface WorkflowNode {
  id: string;
  type: string; // e.g. "IMAGE_GENERATION", "IMAGE_TO_VIDEO", "TEXT_PROMPT", "TEXT_TO_SPEECH"
  params: Record<string, string>;
  status?: string;
  outputUrl?: string | null;
  localPath?: string | null;
  jobId?: string | null;
}

interface WorkflowEdge {
  id: string;
  sourceNodeId: string;
  targetNodeId: string;
  sourcePortId: string;
  targetPortId: string;
}

interface Workflow {
  id: string;
  name: string;
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const apiKey = Deno.env.get("RUNWAY_API_KEY") || "";
  const oneSignalRestKey = Deno.env.get("ONESIGNAL_REST_API_KEY") || "";
  const supabaseUrl = Deno.env.get("SUPABASE_URL") || "";
  const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";

  let body: Record<string, any>;
  try {
    const rawText = await req.text();
    let parsed = JSON.parse(rawText);
    while (typeof parsed === "string") {
      try { parsed = JSON.parse(parsed); } catch { break; }
    }
    body = (parsed && typeof parsed === "object") ? parsed : {};
  } catch {
    return new Response(JSON.stringify({ success: false, error: "Invalid JSON body" }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const { workflow, recipientUserId, activityId } = body as {
    workflow: Workflow;
    recipientUserId?: string;
    activityId?: string;
  };

  if (!workflow || !workflow.nodes) {
    return new Response(JSON.stringify({ success: false, error: "Missing workflow definition" }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  console.log(`[execute-workflow] 🚀 Received cloud workflow execution: ${workflow.name} (${workflow.id}), ${workflow.nodes.length} nodes`);

  const supabase = createClient(supabaseUrl, supabaseServiceKey);

  // Helper to send Live Activity remote updates to OneSignal
  const sendLiveActivityEvent = async (
    actId: string,
    event: "update" | "end",
    eventUpdates: Record<string, unknown>
  ) => {
    if (!actId || !oneSignalRestKey) return;
    try {
      console.log(`[execute-workflow] 📡 Sending Live Activity [${event}] for activity: ${actId}`);
      const res = await fetch(
        `https://onesignal.com/api/v1/apps/${ONESIGNAL_APP_ID}/live_activities/${actId}/notifications`,
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
      console.log(`[execute-workflow] OneSignal Live Activity [${event}] HTTP ${res.status}: ${resText}`);
    } catch (err) {
      console.error(`[execute-workflow] Failed to send Live Activity [${event}] notification:`, err);
    }
  };

  const runEngine = async () => {
    try {
      const nodesMap = new Map<string, WorkflowNode>();
      workflow.nodes.forEach((n) => nodesMap.set(n.id, { ...n }));

      // Topological Sort
      const inDegree = new Map<string, number>();
      const adjList = new Map<string, string[]>();
      workflow.nodes.forEach((n) => inDegree.set(n.id, 0));

      workflow.edges.forEach((e) => {
        inDegree.set(e.targetNodeId, (inDegree.get(e.targetNodeId) || 0) + 1);
        if (!adjList.has(e.sourceNodeId)) adjList.set(e.sourceNodeId, []);
        adjList.get(e.sourceNodeId)!.push(e.targetNodeId);
      });

      const queue: string[] = [];
      inDegree.forEach((deg, id) => { if (deg === 0) queue.push(id); });

      const sorted: string[] = [];
      while (queue.length > 0) {
        const current = queue.shift()!;
        sorted.push(current);
        const neighbors = adjList.get(current) || [];
        for (const neighbor of neighbors) {
          inDegree.set(neighbor, (inDegree.get(neighbor) || 1) - 1);
          if (inDegree.get(neighbor) === 0) {
            queue.push(neighbor);
          }
        }
      }

      const genNodeIds = sorted.filter((id) => {
        const type = nodesMap.get(id)?.type;
        return type !== "TEXT_PROMPT" && type !== "NOTE";
      });

      const totalSteps = genNodeIds.length;
      const stepNodeTypes = genNodeIds.map((id) => nodesMap.get(id)?.type || "");
      const effectiveActivityId = activityId || workflow.id;

      console.log(`[execute-workflow] 📋 Topological execution order: ${genNodeIds.length} runnable steps`);

      // Helper to resolve outputs from connected upstream nodes
      const resolveOutput = (nodeId: string): string | null => {
        const n = nodesMap.get(nodeId);
        if (!n) return null;
        if (n.outputUrl) return n.outputUrl;
        if (n.type === "TEXT_PROMPT") return n.params["text"] || null;
        return null;
      };

      let completedCount = 0;

      for (let stepIdx = 0; stepIdx < genNodeIds.length; stepIdx++) {
        const nodeId = genNodeIds[stepIdx];
        const node = nodesMap.get(nodeId)!;
        const currentStepNum = stepIdx + 1;
        const nodeTypeName = node.type;
        const displayTitle = node.params["title"] || nodeTypeName.toLowerCase().replace(/_/g, " ");

        console.log(`[execute-workflow] ⚙️ Executing step ${currentStepNum}/${totalSteps}: ${node.id} (${nodeTypeName})`);

        // Send initial Live Activity update for this step
        if (effectiveActivityId) {
          await sendLiveActivityEvent(effectiveActivityId, "update", {
            status: totalSteps > 1 ? `Step ${currentStepNum}/${totalSteps}: Generating ${displayTitle}...` : `Generating ${displayTitle}...`,
            nodeTitle: displayTitle,
            workflowName: workflow.name,
            currentStep: currentStepNum,
            totalSteps,
            completedSteps: completedCount,
            stepNodeTypes,
            currentNodeType: nodeTypeName,
            progress: -1.0,
            isFinished: false,
            isSuccess: false,
            timestamp: Date.now() / 1000,
          });
        }

        // Build request body for Runway API based on node type
        let endpoint = "";
        let payload: Record<string, any> = {};

        if (nodeTypeName === "IMAGE_GENERATION") {
          endpoint = "/image_to_video";
          let prompt = node.params["prompt"] || "";
          const refUris: string[] = [];

          workflow.edges.filter((e) => e.targetNodeId === node.id).forEach((e) => {
            if (e.targetPortId === "prompt") {
              const p = resolveOutput(e.sourceNodeId);
              if (p) prompt = p;
            } else if (e.targetPortId === "reference") {
              const ref = resolveOutput(e.sourceNodeId);
              if (ref) refUris.push(ref);
            }
          });

          payload = {
            model: node.params["model"] || "gemini_image3_pro",
            promptText: prompt,
            ratio: node.params["aspectRatio"] || "16:9",
          };
          if (refUris.length > 0) payload.referenceImageUris = refUris.slice(0, 2);
        } else if (nodeTypeName === "IMAGE_TO_VIDEO") {
          endpoint = "/image_to_video";
          const imageUris: string[] = [];
          workflow.edges.filter((e) => e.targetNodeId === node.id && e.targetPortId === "image").forEach((e) => {
            const img = resolveOutput(e.sourceNodeId);
            if (img) imageUris.push(img);
          });

          let prompt = node.params["prompt"] || "";
          workflow.edges.filter((e) => e.targetNodeId === node.id && e.targetPortId === "prompt").forEach((e) => {
            const p = resolveOutput(e.sourceNodeId);
            if (p) prompt = p;
          });

          payload = {
            model: node.params["model"] || "veo3.1_fast",
            promptText: prompt,
            duration: Number(node.params["duration"]) || 5,
            ratio: node.params["aspectRatio"] || "16:9",
          };
          if (imageUris.length > 0) payload.promptImage = imageUris[0];
          if (imageUris.length > 1) payload.lastFrameImage = imageUris[1];
        } else if (nodeTypeName === "TEXT_TO_SPEECH") {
          endpoint = "/text_to_speech";
          let text = node.params["text"] || "";
          workflow.edges.filter((e) => e.targetNodeId === node.id && e.targetPortId === "prompt").forEach((e) => {
            const t = resolveOutput(e.sourceNodeId);
            if (t) text = t;
          });
          payload = {
            text,
            voicePreset: node.params["voicePreset"] || "Maya",
          };
        } else {
          // Default generic request builder for custom nodes
          endpoint = "/image_to_video";
          payload = { ...node.params };
        }

        // Call Runway API
        console.log(`[execute-workflow] 📡 Requesting Runway API ${endpoint}...`);
        const rwRes = await fetch(`${RUNWAY_BASE}${endpoint}`, {
          method: "POST",
          headers: {
            "Authorization": `Bearer ${apiKey}`,
            "X-Runway-Version": RUNWAY_VERSION,
            "Content-Type": "application/json",
          },
          body: JSON.stringify(payload),
        });

        if (!rwRes.ok) {
          const errTxt = await rwRes.text();
          throw new Error(`Runway HTTP ${rwRes.status}: ${errTxt}`);
        }

        const rwData = await rwRes.json();
        const jobId = rwData.id;
        if (!jobId) throw new Error("No taskId returned from Runway API");

        console.log(`[execute-workflow] ✅ Runway taskId created: ${jobId}`);
        node.jobId = jobId;
        node.status = "RUNNING";

        // Poll task status until complete
        let stepSucceeded = false;
        let outputUrl: string | null = null;
        let stepError: string | null = null;

        for (let poll = 0; poll < 180; poll++) {
          await new Promise((r) => setTimeout(r, 5000));

          const pollRes = await fetch(`${RUNWAY_BASE}/tasks/${jobId}`, {
            headers: {
              "Authorization": `Bearer ${apiKey}`,
              "X-Runway-Version": RUNWAY_VERSION,
            },
          });

          if (!pollRes.ok) continue;

          const pollData = await pollRes.json();
          const taskStatus = pollData.status;
          const rawProgressRatio = typeof pollData.progressRatio === "number" ? pollData.progressRatio : -1;

          if (taskStatus === "RUNNING" || taskStatus === "PENDING") {
            if (effectiveActivityId) {
              const statusText = rawProgressRatio > 0
                ? `Step ${currentStepNum}/${totalSteps}: Generating ${displayTitle} (${Math.round(rawProgressRatio * 100)}%)...`
                : `Step ${currentStepNum}/${totalSteps}: Generating ${displayTitle}...`;

              await sendLiveActivityEvent(effectiveActivityId, "update", {
                status: statusText,
                nodeTitle: displayTitle,
                workflowName: workflow.name,
                currentStep: currentStepNum,
                totalSteps,
                completedSteps: completedCount,
                stepNodeTypes,
                currentNodeType: nodeTypeName,
                progress: rawProgressRatio,
                isFinished: false,
                isSuccess: false,
                timestamp: Date.now() / 1000,
              });
            }
          } else if (taskStatus === "SUCCEEDED") {
            stepSucceeded = true;
            outputUrl = pollData.output?.[0] || pollData.artifactUrl || null;
            console.log(`[execute-workflow] 🎉 Step ${currentStepNum}/${totalSteps} SUCCEEDED! outputUrl=${outputUrl}`);
            break;
          } else if (taskStatus === "FAILED") {
            stepError = pollData.failure || pollData.failureCode || "Task failed";
            console.error(`[execute-workflow] ❌ Step ${currentStepNum}/${totalSteps} FAILED: ${stepError}`);
            break;
          }
        }

        if (!stepSucceeded) {
          node.status = "FAILED";
          // End Live Activity on failure
          if (effectiveActivityId) {
            await sendLiveActivityEvent(effectiveActivityId, "end", {
              status: `Step ${currentStepNum} (${displayTitle}) Failed`,
              nodeTitle: displayTitle,
              workflowName: workflow.name,
              currentStep: currentStepNum,
              totalSteps,
              completedSteps: completedCount,
              stepNodeTypes,
              currentNodeType: nodeTypeName,
              progress: completedCount / totalSteps,
              isFinished: true,
              isSuccess: false,
              timestamp: Date.now() / 1000,
            });
          }
          throw new Error(`Step ${currentStepNum} failed: ${stepError || "Timeout"}`);
        }

        // Update node in state
        node.status = "COMPLETED";
        node.outputUrl = outputUrl;
        completedCount++;

        // Persist updated workflow state to Supabase Postgres database `workflows` table
        try {
          const updatedNodesList = Array.from(nodesMap.values());
          await supabase
            .from("workflows")
            .update({ nodes_json: JSON.stringify(updatedNodesList), last_edited: Date.now() })
            .eq("id", workflow.id);
          console.log(`[execute-workflow] 💾 Synced completed step ${currentStepNum} to Supabase DB`);
        } catch (dbErr) {
          console.error(`[execute-workflow] DB sync warning:`, dbErr);
        }

        // Send Live Activity update for completed step
        const isLast = currentStepNum >= totalSteps;
        if (effectiveActivityId) {
          if (!isLast) {
            await sendLiveActivityEvent(effectiveActivityId, "update", {
              status: `Step ${currentStepNum}/${totalSteps}: ${displayTitle} Completed ✓`,
              nodeTitle: displayTitle,
              workflowName: workflow.name,
              currentStep: currentStepNum,
              totalSteps,
              completedSteps: completedCount,
              stepNodeTypes,
              currentNodeType: nodeTypeName,
              progress: completedCount / totalSteps,
              isFinished: false,
              isSuccess: true,
              timestamp: Date.now() / 1000,
            });
          } else {
            await sendLiveActivityEvent(effectiveActivityId, "end", {
              status: `All ${totalSteps} nodes completed! ✓`,
              nodeTitle: displayTitle,
              workflowName: workflow.name,
              currentStep: totalSteps,
              totalSteps,
              completedSteps: totalSteps,
              stepNodeTypes,
              currentNodeType: nodeTypeName,
              progress: 1.0,
              isFinished: true,
              isSuccess: true,
              timestamp: Date.now() / 1000,
            });
          }
        }
      }

      // Send final Push Notification to user upon complete workflow success
      if (recipientUserId && recipientUserId.trim().length > 0 && oneSignalRestKey) {
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
                external_id: [recipientUserId.trim()],
              },
              headings: {
                en: `Workflow Complete (${workflow.name})`,
              },
              contents: {
                en: `All ${totalSteps} nodes generated successfully!`,
              },
              data: {
                workflow_id: workflow.id,
                type: "workflow_complete",
              },
            }),
          });
        } catch (pushErr) {
          console.error(`[execute-workflow] Push notification error:`, pushErr);
        }
      }

    } catch (err: any) {
      console.error(`[execute-workflow] Workflow execution exception:`, err);
    }
  };

  // Launch execution engine in background via EdgeRuntime.waitUntil
  if (typeof (globalThis as any).EdgeRuntime?.waitUntil === "function") {
    (globalThis as any).EdgeRuntime.waitUntil(runEngine());
  } else {
    runEngine();
  }

  return new Response(JSON.stringify({ success: true, message: "Cloud workflow execution started in background" }), {
    status: 200,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
});
