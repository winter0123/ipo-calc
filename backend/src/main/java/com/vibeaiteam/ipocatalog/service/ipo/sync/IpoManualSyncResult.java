package com.vibeaiteam.ipocatalog.service.ipo.sync;

/** 관리자 수동 동기화 결과. */
public record IpoManualSyncResult(
    int created,
    int updated,
    int skipped,
    int failed,
    int scheduleMerged,
    int detailSaved,
    boolean aborted) {}
