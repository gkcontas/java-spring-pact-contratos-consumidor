#!/usr/bin/env bash
# The three broker calls a pipeline makes, as plain curl. In a real pipeline these are
# the pact-broker CLI commands of the same names; the HTTP calls are the same.
#
#   scripts/broker.sh publish <consumer> <version> <branch> <pact-file>
#   scripts/broker.sh can-i-deploy <pacticipant> <version> [environment]
#   scripts/broker.sh record-deployment <pacticipant> <version> [environment]
#
# can-i-deploy exits 0 only when the broker answers deployable=true. "Unknown" (no
# verification yet) is not a yes.
set -euo pipefail

BROKER="${PACT_BROKER_URL:-http://localhost:9292}"

publish() {
  local consumer=$1 version=$2 branch=$3 file=$4
  jq -n --arg c "$consumer" --arg v "$version" --arg b "$branch" \
        --arg content "$(base64 -w0 "$file")" \
     '{pacticipantName: $c, pacticipantVersionNumber: $v, branch: $b,
       contracts: [{consumerName: $c, providerName: "catalog-service", specification: "pact",
                    contentType: "application/json", content: $content}]}' \
  | curl -sf -X POST "$BROKER/contracts/publish" -H 'Content-Type: application/json' -d @- \
  | jq -r '.notices[] | select(.type == "success") | .text'
}

can_i_deploy() {
  local pacticipant=$1 version=$2 environment=${3:-production}
  local summary
  summary=$(curl -sf -G "$BROKER/can-i-deploy" \
              --data-urlencode "pacticipant=$pacticipant" \
              --data-urlencode "version=$version" \
              --data-urlencode "environment=$environment" | jq -c '.summary')
  echo "$summary"
  [ "$(jq -r '.deployable' <<<"$summary")" = "true" ]
}

record_deployment() {
  local pacticipant=$1 version=$2 environment=${3:-production}
  local uuid
  uuid=$(curl -sf "$BROKER/environments" \
           | jq -r --arg e "$environment" '._embedded.environments[] | select(.name == $e) | .uuid')
  curl -sf -X POST "$BROKER/pacticipants/$pacticipant/versions/$version/deployed-versions/environment/$uuid" \
       -H 'Content-Type: application/json' -d '{}' >/dev/null
  echo "recorded $pacticipant $version in $environment"
}

case "${1:-}" in
  publish) shift; publish "$@" ;;
  can-i-deploy) shift; can_i_deploy "$@" ;;
  record-deployment) shift; record_deployment "$@" ;;
  *) sed -n '2,10p' "$0"; exit 2 ;;
esac
