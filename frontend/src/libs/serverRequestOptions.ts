import { headers } from "next/headers";

const FORWARDED_HEADER_NAMES = [
  "x-forwarded-for",
  "x-real-ip",
  "cf-connecting-ip",
  "true-client-ip",
  "x-client-ip",
];

export type ServerRequestOptions = {
  headers: Record<string, string>;
};

export function buildServerRequestOptions(): ServerRequestOptions {
  const incomingHeaders = headers();
  const outgoingHeaders: Record<string, string> = {};
  const cookie = incomingHeaders.get("cookie");

  if (cookie) {
    outgoingHeaders.cookie = cookie;
  }

  FORWARDED_HEADER_NAMES.forEach((headerName) => {
    const headerValue = incomingHeaders.get(headerName);
    if (headerValue) {
      outgoingHeaders[headerName] = headerValue;
    }
  });

  return {
    headers: outgoingHeaders,
  };
}
