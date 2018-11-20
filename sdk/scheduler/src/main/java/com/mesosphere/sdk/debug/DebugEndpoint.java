package com.mesosphere.sdk.debug;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;


/**
 * Interface for all debug endpoints.
 */
public interface DebugEndpoint {

	/**
	 * Called to retun JSON response of requested endpoint. 
	 * @param plan (optional) Plan to drill down on.
	 * @param phase (optional) Phase to drill down on.
	 * @param step (optional) Step to drill down on.
	 * @param sync (optional) Poll backend State-Stores.
	 * @return JSON response of the requested debug endpoint.
	 */
	public Response getJson(@QueryParam("plan") String plan,
			@QueryParam("phase") String phase,
			@QueryParam("step") String step,
			@QueryParam("sync") boolean sync);
}
