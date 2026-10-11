package com.faber.api.base.admin.biz;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 使用启动时的实际扫描和解析器，避免SQL正文测试遗漏迁移文件头。 */
class AppWgtSqlHeaderTest {

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgre"})
    void wgtMigrationPassesStartupHeaderValidation(String databaseType) throws Exception {
        var headers = new SystemUpdateLogBiz().loadAndValidateSqlHeaders("fa-app", databaseType);
        var header = headers.stream()
                .filter(item -> "1.0.13_app_ddl.sql".equals(item.getFileName()))
                .findFirst().orElseThrow();
        assertEquals(1_000_013L, header.getVer());
        assertEquals("V1.0.13", header.getVerNo());
        assertEquals("WGT运行时隔离与Vapor原生基座报告", header.getInfo());
        assertEquals(64, header.getChecksum().length());
    }
}
