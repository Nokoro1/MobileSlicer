import { errorResponse, handleOAuthCallback } from "../../../../lib/thingiverse-oauth.mjs";

export async function onRequestGet({ request, env }) {
  try {
    return await handleOAuthCallback(request, env);
  } catch (error) {
    return errorResponse(error);
  }
}
