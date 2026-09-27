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

      // ── Delegate Live Activity Polling to poll-job Function ──────────
      if (taskId && (liveActivity || recipientUserId)) {
        ctx.waitUntil((async () => {
          try {
            const supabaseUrl = Deno.env.get("SUPABASE_URL") || "";
            const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || Deno.env.get("SUPABASE_ANON_KEY") || "";
            if (supabaseUrl) {
              console.log(`[runway-generate] 🚀 Delegating Live Activity polling to poll-job for task: ${taskId}`);
              await fetch(`${supabaseUrl}/functions/v1/poll-job`, {
                method: "POST",
                headers: {
                  "Content-Type": "application/json",
                  "Authorization": `Bearer ${serviceKey}`,
                },
                body: JSON.stringify({
                  jobId: taskId,
                  provider: "runway",
                  liveActivity,
                  recipientUserId,
                }),
              });
            }
          } catch (delErr) {
            console.error(`[runway-generate] Failed to delegate to poll-job:`, delErr);
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
