import request from "@/libs/request";

export type TagSuggestionScene = "all" | "question" | "post" | "interest";

type BaseResponseListString = {
  code?: number;
  data?: string[];
  message?: string;
};

export async function listTagSuggestionsUsingGet(
  params?: {
    keyword?: string;
    scene?: TagSuggestionScene;
    limit?: number;
  },
  options?: { [key: string]: any },
) {
  return request<BaseResponseListString>("/api/tag/suggest", {
    method: "GET",
    params: {
      ...(params || {}),
    },
    ...(options || {}),
  });
}
