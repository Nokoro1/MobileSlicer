import { errorResponse, handleOAuthRedeem } from "../../../../lib/thingiverse-oauth.mjs";

export async function onRequestGet({ request, env }) {
  try {
    return await handleOAuthRedeem(request, env);
  } catch (error) {
    return errorResponse(error);
  }
}
