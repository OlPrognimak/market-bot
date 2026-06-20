import type { ReactNode } from "react";

export type SortDirection = "asc" | "desc";

export type SortState<Key extends string> = {
  key: Key;
  direction: SortDirection;
};

export type SortColumn<Row, Key extends string> = {
  key: Key;
  label: ReactNode;
  value: (row: Row) => string | number | boolean | null | undefined;
};

export function nextSort<Key extends string>(current: SortState<Key>, key: Key): SortState<Key> {
  return current.key === key
    ? { key, direction: current.direction === "asc" ? "desc" : "asc" }
    : { key, direction: "asc" };
}

export function sortRows<Row, Key extends string>(
  rows: Row[],
  sort: SortState<Key>,
  columns: Array<SortColumn<Row, Key>>
) {
  const column = columns.find((item) => item.key === sort.key);
  if (!column) {
    return rows;
  }

  return [...rows].sort((left, right) => {
    const result = compareValues(column.value(left), column.value(right));
    return sort.direction === "asc" ? result : -result;
  });
}

export function sortButtonLabel<Key extends string>(sort: SortState<Key>, key: Key, label: ReactNode) {
  const suffix = sort.key === key ? (sort.direction === "asc" ? " ▲" : " ▼") : "";
  return `${String(label)}${suffix}`;
}

function compareValues(
  left: string | number | boolean | null | undefined,
  right: string | number | boolean | null | undefined
) {
  if (left == null && right == null) return 0;
  if (left == null) return 1;
  if (right == null) return -1;
  if (typeof left === "number" && typeof right === "number") {
    return left - right;
  }
  if (typeof left === "boolean" && typeof right === "boolean") {
    return Number(left) - Number(right);
  }
  return String(left).localeCompare(String(right), undefined, { numeric: true, sensitivity: "base" });
}
