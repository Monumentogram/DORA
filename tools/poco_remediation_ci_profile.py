"""Only preserve content-free persistence diagnostics; every accepted gate remains intact."""
ANCHOR = '      - name: Verify abrupt process death at durable persistence phases\n'
UPLOAD = '''      - name: Preserve content-free persistence instrumentation diagnostics
        if: always()
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1
        with:
          name: persistence-instrumentation-diagnostics-api${{ matrix.api }}
          path: ${{ runner.temp }}/persistence-runtime-receipt-diagnostics/*.json*
          if-no-files-found: warn
          retention-days: 7

'''


def upgrade(workflow):
    if workflow.count(ANCHOR) != 1 or 'Preserve content-free persistence instrumentation diagnostics' in workflow:
        raise ValueError('Diagnostic upload anchor changed')
    return workflow.replace(ANCHOR, UPLOAD + ANCHOR)


def normalize(workflow):
    workflow = normalize_budget(workflow)
    if 'persistence-instrumentation-diagnostics-api' not in workflow and 'Preserve content-free persistence instrumentation diagnostics' not in workflow:
        return workflow
    if workflow.count(UPLOAD) != 1:
        raise ValueError('Diagnostic preservation changed')
    original = workflow.replace(UPLOAD, '', 1)
    if upgrade(original) != workflow:
        raise ValueError('Diagnostic preservation order changed')
    return original


# Owner-approved aggregate API28 budget only; all per-command deadlines stay fixed.
BUDGET_BASE = '  encrypted-persistence:\n    name: encrypted-persistence-api${{ matrix.api }}\n    runs-on: ubuntu-latest\n    timeout-minutes: 45'
BUDGET_APPROVED = '  encrypted-persistence:\n    name: encrypted-persistence-api${{ matrix.api }}\n    runs-on: ubuntu-latest\n    timeout-minutes: ${{ matrix.api == 28 && 60 || 45 }}'


def normalize_budget(workflow):
    if '  encrypted-persistence:' not in workflow:
        return workflow
    if workflow.count(BUDGET_APPROVED) == 1:
        return workflow.replace(BUDGET_APPROVED, BUDGET_BASE, 1)
    if workflow.count(BUDGET_BASE) != 1:
        raise ValueError('Unapproved persistence job budget')
    return workflow
