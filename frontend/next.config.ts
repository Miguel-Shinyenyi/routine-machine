import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Docker deployment copies this self-contained build output into a minimal runtime image
  // instead of shipping node_modules -- see frontend/Dockerfile.
  output: "standalone",
};

export default nextConfig;
