"use client";

import { Pagination } from "antd";
import { usePathname, useRouter, useSearchParams } from "next/navigation";

interface Props {
  current: number;
  pageSize: number;
  total: number;
  anchorId?: string;
}

const BankQuestionPagination = ({ current, pageSize, total, anchorId }: Props) => {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const handlePageChange = (page: number) => {
    const params = new URLSearchParams(searchParams?.toString() || "");
    if (page <= 1) {
      params.delete("page");
    } else {
      params.set("page", String(page));
    }
    const queryString = params.toString();
    const hash = anchorId ? `#${anchorId}` : "";
    router.push(`${pathname}${queryString ? `?${queryString}` : ""}${hash}`, { scroll: false });
  };

  return (
    <div className="flex justify-center sm:justify-end">
      <Pagination
        current={current}
        pageSize={pageSize}
        total={total}
        showSizeChanger={false}
        onChange={handlePageChange}
      />
    </div>
  );
};

export default BankQuestionPagination;
