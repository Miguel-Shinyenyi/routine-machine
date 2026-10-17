import { describe, expect, it } from "vitest";
import { markQuotes } from "./markQuotes";

describe("markQuotes", () => {
  it("returns the whole note unmarked when there are no quoted phrases", () => {
    expect(markQuotes("Hello world", [])).toEqual([{ text: "Hello world", quoted: false }]);
  });

  it("marks a single quoted phrase found in the note", () => {
    expect(markQuotes("Harari says we don't know and that's fine", ["we don't know"])).toEqual([
      { text: "Harari says ", quoted: false },
      { text: "we don't know", quoted: true },
      { text: " and that's fine", quoted: false },
    ]);
  });

  it("marks multiple non-overlapping phrases", () => {
    const result = markQuotes("The plateau of latent potential is real. Also the dip.", [
      "plateau of latent potential",
      "the dip",
    ]);
    expect(result.filter((s) => s.quoted).map((s) => s.text)).toEqual([
      "plateau of latent potential",
      "the dip",
    ]);
  });

  it("silently skips a phrase not found verbatim in the note", () => {
    expect(markQuotes("Hello world", ["not in here"])).toEqual([{ text: "Hello world", quoted: false }]);
  });
});
