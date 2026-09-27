import "@supabase/functions-js/edge-runtime.d.ts";

const RUNWAY_BASE = "https://api.dev.runwayml.com/v1";
const RUNWAY_VERSION = "2024-11-06";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const apiKey = Deno.env.get("RUNWAY_API_KEY");
  if (!apiKey) {
    return new Response(JSON.stringify({ success: false, error: "Server configuration error" }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

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

  const { taskId } = body;
  if (!taskId) {
    return new Response(JSON.stringify({ success: false, error: "Missing 'taskId'" }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  try {
    const response = await fetch(`${RUNWAY_BASE}/tasks/${taskId}`, {
      headers: {
        "Authorization": `Bearer ${apiKey}`,
        "X-Runway-Version": RUNWAY_VERSION,
      },
    });

    const data = await response.json();

    if (!response.ok) {
      return new Response(JSON.stringify({ success: false, error: `Runway ${response.status}`, data }), {
        status: response.status,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    return new Response(JSON.stringify({ success: true, data }), {
      status: 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (err: any) {
    return new Response(JSON.stringify({ success: false, error: err.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
