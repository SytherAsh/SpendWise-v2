import { fileURLToPath } from "node:url";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./vitest.setup.ts"],
    include: ["src/**/*.{test,spec}.{ts,tsx}"],
    css: false,
    // Recharts renders SVG slowly under jsdom; the default 5s flakes on a loaded
    // machine (e.g. when the dev stack is also running). Give tests more headroom.
    testTimeout: 20000,
    hookTimeout: 20000,
  },
});
