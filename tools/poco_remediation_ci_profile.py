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
    if 'persistence-instrumentation-diagnostics-api' not in workflow and 'Preserve content-free persistence instrumentation diagnostics' not in workflow:
        return workflow
    if workflow.count(UPLOAD) != 1:
        raise ValueError('Diagnostic preservation changed')
    original = workflow.replace(UPLOAD, '', 1)
    if upgrade(original) != workflow:
        raise ValueError('Diagnostic preservation order changed')
    return original
