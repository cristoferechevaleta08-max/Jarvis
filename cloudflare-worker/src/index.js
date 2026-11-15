// JARVIS Cloud AI gateway — deploy as a Cloudflare Worker.
// Configure OPENAI_API_KEY as a Worker secret before deploying.
const SYSTEM_PROMPT = `Eres JARVIS, un asistente personal inteligente. Hablas siempre en español claro y natural, con tono sereno, preciso y servicial. Responde directamente a lo que te preguntan, explica cuando haga falta y reconoce honestamente cuando no sabes algo. No inventes datos ni afirmes haber ejecutado acciones en el teléfono. Si una petición requiere acceso al dispositivo, explica qué permiso o integración sería necesaria.`;

export default {
  async fetch(request, env) {
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
      "Content-Type": "application/json; charset=utf-8"
    };
    if (request.method === "OPTIONS") return new Response(null, { headers: cors });
    if (request.method !== "POST") return json({ error: "Método no permitido." }, 405, cors);
    if (!env.OPENAI_API_KEY) return json({ error: "Falta configurar OPENAI_API_KEY en el servidor." }, 503, cors);

    try {
      const body = await request.json();
      const messages = Array.isArray(body.messages) ? body.messages.slice(-13) : [];
      if (!messages.length || messages.some(m =>
        !["user", "assistant"].includes(m.role) ||
        typeof m.content !== "string" || m.content.length > 5000
      )) return json({ error: "Formato de conversación no válido." }, 400, cors);

      const input = messages.map(m => ({
        role: m.role,
        content: m.content
      }));
      const upstream = await fetch("https://api.openai.com/v1/responses", {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${env.OPENAI_API_KEY}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          model: env.OPENAI_MODEL || "gpt-5.5",
          instructions: SYSTEM_PROMPT,
          input,
          store: false,
          max_output_tokens: 900
        })
      });
      const data = await upstream.json();
      if (!upstream.ok) {
        return json({ error: data.error?.message || "El proveedor de IA rechazó la solicitud." }, 502, cors);
      }
      const answer = data.output_text || (data.output || [])
        .flatMap(item => item.content || [])
        .filter(part => part.type === "output_text")
        .map(part => part.text).join("\n");
      if (!answer) return json({ error: "La IA no generó texto." }, 502, cors);
      return json({ answer }, 200, cors);
    } catch (error) {
      return json({ error: error instanceof Error ? error.message : "Error interno." }, 500, cors);
    }
  }
};

function json(data, status, headers) {
  return new Response(JSON.stringify(data), { status, headers });
}
