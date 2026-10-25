import { describe, expect, it } from "vitest";
import { ApiError } from "./api";
import { fetchServices, serviceTone } from "./watchtower";

describe("fetchServices", () => {
  it("returns the services the backend lists", async () => {
    const services = await fetchServices(async () => [{ name: "backend", status: "UP" }] as never);
    expect(services).toEqual([{ name: "backend", status: "UP" }]);
  });

  it("returns null on a 404, which is how the backend says this person may not see the page", async () => {
    const services = await fetchServices(async () => {
      throw new ApiError(404, "");
    });
    expect(services).toBeNull();
  });

  it("does not hide any other failure as a 404", async () => {
    await expect(
      fetchServices(async () => {
        throw new ApiError(500, "boom");
      }),
    ).rejects.toThrow("boom");
    await expect(
      fetchServices(async () => {
        throw new Error("connection refused");
      }),
    ).rejects.toThrow("connection refused");
  });
});

describe("serviceTone", () => {
  it("names UP as good, DOWN as bad and anything else as neutral", () => {
    expect(serviceTone("UP")).toBe("good");
    expect(serviceTone("DOWN")).toBe("bad");
    expect(serviceTone("NOT_CONFIGURED")).toBe("neutral");
    expect(serviceTone("something new")).toBe("neutral");
  });
});
