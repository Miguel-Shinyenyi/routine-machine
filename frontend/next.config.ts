import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Docker deployment copies this self-contained build output into a minimal runtime image
  // instead of shipping node_modules -- see frontend/Dockerfile.
  output: "standalone",

  // Phase 5 renamed the four views to the shepherd's language. Old links (bookmarks, anything
  // that still points at /board etc.) keep working via a redirect rather than a 404.
  async redirects() {
    return [
      { source: "/board", destination: "/today", permanent: false },
      { source: "/roadmap", destination: "/path", permanent: false },
      { source: "/backlog", destination: "/flock", permanent: false },
      { source: "/reports", destination: "/tally", permanent: false },
    ];
  },
};

export default nextConfig;
