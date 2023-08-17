__kernel void ellipsoid_contains(

		//ellipsoid centre
		__const double3 C,

		// ellipsoid tensor H
		__const double3 Ha,
		__const double3 Hb,
		__const double3 Hc,

		//n boundary points, as a double3 vector stream
		__global const double3 *a,

		//n dot product output for each boundary point for further processing
		__global double *dots
 ) {
	int gid = get_global_id(0);

	//get the boundary point (x, y, z) relative to ellipsoid centre (centre both on common origin)
	const double3 V = a[gid] - C;

	//calculate dot product
	double3 D = (double3) (dot(V, Ha), dot(V, Hb), dot(V, Hc));
	dots[gid] = dot(D, V);
}