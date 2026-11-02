#!/usr/bin/env python3
"""检查 clean verify 产生的报告，拒绝失败、跳过或缺失的关键集成测试。"""
import argparse
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

REQUIRED_SUITES = {
    'com.agri_system.infrastructure.migration.MysqlRetailFactChainIntegrationTest',
    'com.agri_system.infrastructure.migration.LegacyCleanupMigrationTest',
    'com.agri_system.e2e.RetailFactChainEndToEndTest',
}
AUTHORITY_SUITE = 'com.agri_system.authority.AuthorityHttpIntegrationTest'
AUTHORITY_MYSQL_CASE = 'Lifecycle创建等待管理员锁后拒绝刚被停用的目标角色'

def verify(root: Path) -> dict:
    reports = sorted(root.glob('*/target/surefire-reports/TEST-*.xml'))
    if not reports:
        raise ValueError('没有找到测试报告，请先执行完整 clean verify')
    suites = set()
    authority_mysql_ran = False
    total = 0
    for path in reports:
        try:
            suite = ET.parse(path).getroot()
            cases = suite.findall('testcase')
            counts = {key: int(suite.attrib[key]) for key in ('tests', 'failures', 'errors', 'skipped')}
            if suite.tag != 'testsuite' or counts['tests'] <= 0 or counts['tests'] != len(cases):
                raise ValueError('测试数量为空或与明细不一致')
            if any(counts[key] != 0 for key in ('failures', 'errors', 'skipped')):
                raise ValueError('存在失败、错误或跳过')
            if any(case.find(tag) is not None for case in cases for tag in ('failure', 'error', 'skipped')):
                raise ValueError('测试明细存在失败、错误或跳过')
            name = suite.attrib['name']
            suites.add(name)
            if name == AUTHORITY_SUITE:
                authority_mysql_ran |= any(case.get('name') == AUTHORITY_MYSQL_CASE for case in cases)
            total += len(cases)
        except (ET.ParseError, KeyError, ValueError, OSError) as error:
            raise ValueError(f'{path.relative_to(root)}: 测试报告不合格（{error}）') from error
    missing = REQUIRED_SUITES - suites
    if missing or not authority_mysql_ran:
        raise ValueError('缺少关键MySQL/权限并发验收：' + ', '.join(sorted(missing | ({AUTHORITY_MYSQL_CASE} if not authority_mysql_ran else set()))))
    return {'reports': len(reports), 'tests': total, 'failures': 0, 'errors': 0, 'skipped': 0}

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.root.resolve()), ensure_ascii=False))
        return 0
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return 1

if __name__ == '__main__':
    raise SystemExit(main())
