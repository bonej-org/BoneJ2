__kernel void ellipsoid_contains(

		//centre and tensor as a single float16 
		__const float16 CH,

		//n boundary points, as a double3 vector stream
		__global const float3 *a,

		//n dot product output for each boundary point for further processing
		__global float *dots
 ) {
	const float3 C = (float3) (CH.s0, CH.s1, CH.s2);
	const float3 Ha = (float3) (CH.s4, CH.s5, CH.s6);
	const float3 Hb = (float3) (CH.s8, CH.s9, CH.sa);
	const float3 Hc = (float3) (CH.sc, CH.sd, CH.se);
		
	int gid = get_global_id(0);

	//get the boundary point (x, y, z) relative to ellipsoid centre (centre both on common origin)
	const float3 V = a[gid] - C;

	//calculate dot product
	float3 D = (float3) (dot(V, Ha), dot(V, Hb), dot(V, Hc));
	dots[gid] = dot(D, V);
}