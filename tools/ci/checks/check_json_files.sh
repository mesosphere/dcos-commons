import json

if jq -e . >/dev/null 2>&1 <<< `cat ${CHANGESET}`; then
    echo "Parsed JSON successfully and got something other than false/null"
else
    echo "Json check failed"
fi
