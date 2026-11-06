package com.example.dbtabs.structure;

/**
 * Identity of the table whose structure is shown. Kept minimal and serializable-by-hand:
 * a stable string id is used as the key of the VirtualFile cache.
 */
public record TableKey(String dataSourceId, String schema, String table) {

  public String id() {
    return dataSourceId + '/' + schema + '/' + table;
  }

  /** Nice tab title, e.g. {@code ORDERS (structure)}. */
  public String tabTitle() {
    return table + " (structure)";
  }
}
