import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// React Testing Library does not auto-clean between tests under Vitest's globals mode
// unless we wire it up here — do it once for the whole suite.
afterEach(() => {
  cleanup();
});

// jsdom doesn't implement these, but Radix's Select (and other Radix primitives) call them
// during pointer-driven open/select interactions — without a stub, userEvent.click() on a
// Radix trigger/item throws "not a function" in every test that opens one.
if (!Element.prototype.hasPointerCapture) {
  Element.prototype.hasPointerCapture = () => false;
}
if (!Element.prototype.setPointerCapture) {
  Element.prototype.setPointerCapture = () => {};
}
if (!Element.prototype.releasePointerCapture) {
  Element.prototype.releasePointerCapture = () => {};
}
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {};
}
if (typeof globalThis.ResizeObserver === "undefined") {
  // Radix's popper-based positioning (Select.Content, Popover, Tooltip) observes size
  // changes to reposition itself; jsdom has no layout engine so there's nothing to
  // actually observe, but the class must exist or mounting throws immediately.
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
}
