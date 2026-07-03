import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// React Testing Library does not auto-clean between tests under Vitest's globals mode
// unless we wire it up here — do it once for the whole suite.
afterEach(() => {
  cleanup();
});
