#! /bin/bash

echo PR_TEST_ID_ECDSA: $PR_TEST_ID_ECDSA
echo PR_TEST_URL: $PR_TEST_URL

if [ "$PR_TEST_ID_ECDSA" == "" ]
then
    echo env var PR_TEST_ID_ECDSA needs to be set to pr-test private key
    exit 1
fi

if [ "$PR_TEST_URL" == "" ]
then
    echo env var PR_TEST_URL needs to be set to server URL
    exit 1
fi

if [ "$PR_TEST_GRAAS_VENV" == "" ]
then
    echo env var PR_TEST_GRAAS_VENV needs to be set to python venv created from server/app-engine/requirements.txt
    exit 1
fi

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
echo $SCRIPT_DIR

. $PR_TEST_GRAAS_VENV/bin/activate
python3 server/tests/unit-tests.py
EXIT_CODE=$?

if [ "$EXIT_CODE" != "0" ]
then
    echo "python unit tests failed"
    echo "smoke test incomplete"
    exit 1
fi

pushd $SCRIPT_DIR/../server/tools/eslint
./lint-files.sh
EXIT_CODE=$?
popd

if [ "$EXIT_CODE" != "0" ]
then
    echo "javascript linting failed"
    echo "smoke test incomplete"
    exit 1
else
    echo "javscript linting complete"
fi

pushd $SCRIPT_DIR/../gtfu
./gradlew clean build
EXIT_CODE=$?
popd

if [ "$EXIT_CODE" != "0" ]
then
    echo "gtfu gradle build failed, see gtfu/build/reports/tests/test/index.html for details"
    echo "smoke test incomplete"
    exit 1
fi

pushd $SCRIPT_DIR/../server/tests
NODE_PATH=../node/node_modules node test-position-update.js -u $PR_TEST_URL -a pr-test -e PR_TEST_ID_ECDSA
EXIT_CODE=$?
popd

if [ "$EXIT_CODE" != "0" ]
then
    echo "node position update test failed"
    echo "smoke test incomplete"
    exit 1
fi

pushd $SCRIPT_DIR/../server/tests
NODE_PATH=../node/node_modules node post-service-alerts.js -u $PR_TEST_URL -a pr-test -e PR_TEST_ID_ECDSA
EXIT_CODE=$?
popd

if [ "$EXIT_CODE" != "0" ]
then
    echo "node service alert test failed"
    echo "smoke test incomplete"
    exit 1
fi

pushd $SCRIPT_DIR/../server/tests
NODE_PATH=../node/node_modules node post-stop-time-entities.js -u $PR_TEST_URL -a pr-test -e PR_TEST_ID_ECDSA
EXIT_CODE=$?
popd

if [ "$EXIT_CODE" != "0" ]
then
    echo "node stop time entity test failed"
    echo "smoke test incomplete"
    exit 1
fi

echo "smoke test PASSED"

: '
        run: NODE_PATH=../node/node_modules node test-position-update.js -u https://lat-long-prototype.wl.r.appspot.com -a pr-test -e PR_TEST_ID_ECDSA
        env:
          PR_TEST_ID_ECDSA: ${{ secrets.PR_TEST_ID_ECDSA }}
      - name: Run service alert script
        working-directory: server/tests/
        run: NODE_PATH=../node/node_modules node post-service-alerts.js -u https://lat-long-prototype.wl.r.appspot.com -a pr-test -e PR_TEST_ID_ECDSA
        env:
          PR_TEST_ID_ECDSA: ${{ secrets.PR_TEST_ID_ECDSA }}
      - name: Run trip updates script
        working-directory: server/tests/
        run: NODE_PATH=../node/node_modules node post-stop-time-entities.js -u https://lat-long-prototype.wl.r.appspot.com -a pr-test -e PR_TEST_ID_ECDSA
        env:
          PR_TEST_ID_ECDSA: ${{ secrets.PR_TEST_ID_ECDSA }}
      - name: Run eslint
        working-directory: server/tools/eslint/
        run: ./lint-files.sh
      - name: Run Python unit tests
        uses: actions/setup-python@v2
        with:
          python-version: '3.9.2'
      - run: |
          pip install -r server/app-engine/requirements.txt
          python server/tests/unit-tests.py
'
