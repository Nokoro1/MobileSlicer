import { errorResponse, handleOAuthStart } from "../../../../lib/thingiverse-oauth.mjs";

export async function onRequestGet({ request, env }) {
  try {
    return await handleOAuthStart(request, env);
  } catch (error) {
    return errorResponse(error);
  }
}
