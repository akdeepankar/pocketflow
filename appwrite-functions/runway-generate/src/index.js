/**
 * PocketFlow — Appwrite Function: runway-generate
 *
 * Runtime: node-18.0
 * Purpose: Proxies Runway API task submissions server-side so the API key
 *          never lives in the client app.
 *
 * Deploy this file as the entrypoint (src/index.js) of the Appwrite Function.
 *
 * Environment variables (set in Appwrite Console → Functions → runway-generate → Settings):
 *   RUNWAY_API_KEY  — Your Runway API key
 *
 * Request body (JSON):
 *   {
 *     "endpoint": "/image_to_video",   // Runway API path
 *     "payload": { ... }               // Body to forward to Runway
 *   }
 *
 * Response:
 *   Success: { "success": true,  "jobId": "runway-task-id", "data": { ... } }
 *   Error:   { "success": false, "error": "error message" }
 */

const RUNWAY_BASE = "https://api.dev.runwayml.com/v1";
const RUNWAY_VERSION = "2024-11-06";

export default async ({ req, res, log, error }) => {
  const apiKey = process.env.RUNWAY_API_KEY;
  if (!apiKey) {
    error("RUNWAY_API_KEY environment variable is not set");
    return res.json({ success: false, error: "Server configuration error" }, 500);
  }

  let body;
  try {
    body = typeof req.body === "string" ? JSON.parse(req.body) : req.body;
  } catch (e) {
    return res.json({ success: false, error: "Invalid request body" }, 400);
  }

  const { endpoint, payload } = body;

  if (!endpoint || !payload) {
    return res.json({ success: false, error: "Missing 'endpoint' or 'payload' in request" }, 400);
  }

  log(`Calling Runway API: ${endpoint}`);

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
      error(`Runway API error ${response.status}: ${JSON.stringify(data)}`);
      return res.json(
        { success: false, error: `Runway ${response.status}: ${JSON.stringify(data)}` },
        response.status
      );
    }

    log(`Runway task created: ${data.id}`);
    return res.json({ success: true, jobId: data.id, data });

  } catch (err) {
    error(`Exception calling Runway: ${err.message}`);
    return res.json({ success: false, error: err.message }, 500);
  }
};
