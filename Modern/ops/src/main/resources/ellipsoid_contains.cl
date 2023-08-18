__kernel void ellipsoid_contains(

		//ellipsoid centre
		__const float3 C,

		// ellipsoid tensor H
		__const float3 Ha,
		__const float3 Hb,
		__const float3 Hc,

		//n boundary points, as a double3 vector stream
		__global const float3 *a,

		//n dot product output for each boundary point for further processing
		__global float *dots
 ) {
	int gid = get_global_id(0);

	//get the boundary point (x, y, z) relative to ellipsoid centre (centre both on common origin)
	const float3 V = a[gid] - C;

	//calculate dot product
	float3 D = (float3) (dot(V, Ha), dot(V, Hb), dot(V, Hc));
	dots[gid] = dot(D, V);
}