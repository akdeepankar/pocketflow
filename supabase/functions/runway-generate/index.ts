import "@supabase/functions-js/edge-runtime.d.ts";
import { withSupabase } from "@supabase/server";

const RUNWAY_BASE = "https://api.dev.runwayml.com/v1";
const RUNWAY_VERSION = "2024-11-06";

export default {
  fetch: withSupabase({ auth: ["publishable", "secret"] }, async (req) => {
    const apiKey = Deno.env.get("RUNWAY_API_KEY");
    if (!apiKey) {
      console.error("RUNWAY_API_KEY environment variable is not set");
      return Response.json({ success: false, error: "Server configuration error" }, { status: 500 });
    }

    let body;
    try {
      body = await req.json();
    } catch {
      return Response.json({ success: false, error: "Invalid request body" }, { status: 400 });
    }

    const { endpoint, payload } = body;

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

      return Response.json({ success: true, jobId: taskId, data });

    } catch (err) {
      console.error(`Exception calling Runway: ${err.message}`);
      return Response.json({ success: false, error: err.message }, { status: 500 });
    }
  }),
};
