#!/usr/bin/env bash
# Demonstracao ponta a ponta das 4 funcionalidades + controle de acesso.
# Uso: API=http://localhost:8080 bash docs/demo-e2e.sh
# FIXED= (vazio, mas definido) cria logins fixos: lider@demo.test, carol@demo.test... senha secret123
set -euo pipefail
API=${API:-http://localhost:8080}
TMP=target/demo-tmp; mkdir -p "$TMP"
j() { python -c "import sys,json;d=json.load(sys.stdin);print(eval(sys.argv[1]))" "$1"; }
call() { # metodo token caminho [json]
  local m=$1 t=$2 p=$3 b=${4:-}
  if [ -n "$b" ]; then curl -s -X "$m" "$API$p" -H "Authorization: Bearer $t" -H 'Content-Type: application/json' -d "$b"
  else curl -s -X "$m" "$API$p" -H "Authorization: Bearer $t"; fi
}
code() { curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $1" "$API$2"; }
ok() { printf '  \033[32mOK\033[0m %s\n' "$*"; }
check() { [ "$1" = "$2" ] && ok "$3 ($1)" || { printf '  \033[31mFALHOU\033[0m %s: esperado %s, veio %s\n' "$3" "$2" "$1"; exit 1; }; }
reg() {
  curl -s -X POST "$API/api/auth/register" -H 'Content-Type: application/json' \
    -d "{\"name\":\"$1\",\"email\":\"$1${FIXED-.$RANDOM$RANDOM}@demo.test\",\"password\":\"secret123\",\"stageName\":\"$1\",\"mainSpecialty\":\"$2\"}"
}

echo "== Usuarios"
L=$(reg Lider Guitarra); LT=$(echo "$L" | j 'd["token"]'); LA=$(echo "$L" | j 'd["artistId"]')
C=$(reg Carol Baixo);    CT=$(echo "$C" | j 'd["token"]'); CA=$(echo "$C" | j 'd["artistId"]')
D=$(reg Davi Baixo);     DT=$(echo "$D" | j 'd["token"]'); DA=$(echo "$D" | j 'd["artistId"]')
X=$(reg Intruso Bateria); XT=$(echo "$X" | j 'd["token"]')
ok "Lider=$LA Carol=$CA Davi=$DA Intruso criado"

call PUT "$CT" "/api/artists/$CA" '{"stageName":"Carol","mainSpecialty":"Baixo","availability":"Noites","city":"Sao Paulo","instruments":["Baixo"],"musicalStyles":["MPB","Samba Rock"],"influences":["Jorge Ben"]}' >/dev/null
call PUT "$DT" "/api/artists/$DA" '{"stageName":"Davi","mainSpecialty":"Baixo","availability":"Fins de semana","city":"Recife","instruments":["Baixo"],"musicalStyles":["Rock"],"influences":[]}' >/dev/null
check "$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$API/api/artists/$CA" -H "Authorization: Bearer $XT" -H 'Content-Type: application/json' -d '{"stageName":"hack","mainSpecialty":"x","availability":"x"}')" 403 "Intruso nao edita perfil alheio"

echo "== F1: Correspondencia de musicos"
P=$(call POST "$LT" /api/projects '{"title":"Banda Demo","musicalStyle":"MPB","status":"ACTIVE","startDate":"2026-11-01","location":"Sao Paulo","needs":["Baixo"],"desiredInfluences":["Jorge Ben"]}' | j 'd["id"]')
ok "Vaga $P criada (lider entra como membro automaticamente)"
printf 'ID3-audio-demo' > "$TMP/demo.mp3"
AUDIO=$(curl -s -X POST "$API/api/artists/$CA/audio" -H "Authorization: Bearer $CT" -F "file=@$TMP/demo.mp3;type=audio/mpeg" | j 'd["audioUrl"]')
check "$(curl -s -o /dev/null -w '%{http_code}' "$API$AUDIO")" 200 "Audio do perfil abre por link assinado"
check "$(curl -s -o /dev/null -w '%{http_code}' "$API${AUDIO%%\?*}")" 403 "Audio sem assinatura e bloqueado"
APP=$(call POST "$CT" "/api/projects/$P/applications" "{\"artistId\":$CA,\"message\":\"Toco MPB\",\"specialty\":\"Baixo\",\"availability\":\"Noites\"}" | j 'd["id"]')
call POST "$DT" "/api/projects/$P/applications" "{\"artistId\":$DA,\"message\":\"Topo\",\"specialty\":\"Baixo\",\"availability\":\"FDS\"}" >/dev/null
check "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/api/projects/$P/applications" -H "Authorization: Bearer $XT" -H 'Content-Type: application/json' -d "{\"artistId\":$CA,\"message\":\"fake\",\"specialty\":\"x\",\"availability\":\"x\"}")" 403 "Intruso nao se candidata como Carol"
echo "  Ranking so de candidatos (appliedOnly=true):"
call GET "$LT" "/api/projects/$P/matches?appliedOnly=true" | j '"\n".join("    %s: %s pts" % (m["stageName"], m["score"]) for m in d)'
check "$(call GET "$LT" "/api/projects/$P/matches?appliedOnly=true" | j 'len(d)')" 2 "Ranking restrito aos 2 candidatos"
check "$(call GET "$LT" "/api/projects/$P/applications/ranked?city=Sao%20Paulo" | j 'len(d)')" 1 "Filtro por cidade nos candidatos"
check "$(code "$XT" "/api/projects/$P/matches")" 403 "Intruso nao ve ranking"
call PATCH "$LT" "/api/applications/$APP/status" '{"status":"APPROVED","feedback":"Bem-vinda!"}' >/dev/null
check "$(call GET "$CT" "/api/notifications" | j 'any(n["type"]=="APPLICATION_STATUS" or "andidatura" in n["title"] for n in d)')" True "Carol recebeu retorno da candidatura"
ok "Carol aprovada e agora e membro"

echo "== F2: Decisoes, arquivos e versoes"
printf 'arranjo v1' > "$TMP/arranjo.pdf"; printf 'arranjo v2' > "$TMP/arranjo2.pdf"
F=$(curl -s -X POST "$API/api/projects/$P/files" -H "Authorization: Bearer $LT" -F "file=@$TMP/arranjo.pdf;type=application/pdf" -F name=Arranjo -F category=ARRANGEMENT | j 'd["id"]')
R=$(curl -s -X POST "$API/api/files/$F/versions" -H "Authorization: Bearer $CT" -F "file=@$TMP/arranjo2.pdf;type=application/pdf" -F note="Ponte nova")
V1=$(echo "$R" | j '[v for v in d["versions"] if v["versionNumber"]==1][0]["url"]')
V2ID=$(echo "$R" | j '[v for v in d["versions"] if v["versionNumber"]==2][0]["id"]')
V1ID=$(echo "$R" | j '[v for v in d["versions"] if v["versionNumber"]==1][0]["id"]')
echo "$R" | j '"\n".join("    v%s por %s sha=%s..." % (v["versionNumber"], v["uploadedByName"], v["sha256"][:10]) for v in d["versions"])'
check "$(curl -s "$API$V1")" "arranjo v1" "Link da v1 (antiga) segue imutavel"
check "$(code "$XT" "/api/files/$F/versions/1")" 403 "Intruso nao acessa versoes"
check "$(code "$XT" "/api/projects/$P/files")" 403 "Intruso nao lista arquivos"
POLL=$(call POST "$LT" "/api/projects/$P/polls" "{\"question\":\"Qual arranjo?\",\"options\":[{\"label\":\"v2\",\"fileVersionId\":$V2ID},{\"label\":\"v1\",\"fileVersionId\":$V1ID}]}")
PID=$(echo "$POLL" | j 'd["id"]'); OID=$(echo "$POLL" | j 'd["options"][0]["id"]')
call POST "$LT" "/api/polls/$PID/votes" "{\"optionId\":$OID}" >/dev/null
call POST "$CT" "/api/polls/$PID/votes" "{\"optionId\":$OID}" >/dev/null
check "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/api/polls/$PID/votes" -H "Authorization: Bearer $XT" -H 'Content-Type: application/json' -d "{\"optionId\":$OID}")" 403 "Intruso nao vota"
call POST "$LT" "/api/polls/$PID/close" '{}' >/dev/null
check "$(call GET "$LT" "/api/files/$F" | j 'd["locked"]')" True "Enquete encerrada travou a versao decidida"
check "$(call GET "$LT" "/api/projects/$P/audit-logs" | j 'len(d)>=3')" True "Log de auditoria registrado"

echo "== F3: Tarefas, timeline e alertas"
T=$(call POST "$LT" "/api/projects/$P/tasks" "{\"title\":\"Gravar baixo\",\"status\":\"TODO\",\"priority\":\"HIGH\",\"responsibleName\":\"Carol\",\"stage\":\"RECORDING\",\"startDate\":\"2026-11-10\",\"dueDate\":\"2026-11-15\",\"assigneeArtistId\":$CA}" | j 'd["id"]')
check "$(call GET "$CT" "/api/notifications?unreadOnly=true" | j 'any(n["type"]=="TASK_ASSIGNED" for n in d)')" True "Carol alertada da atribuicao"
check "$(call GET "$LT" "/api/projects/$P/timeline" | j 'd["currentStage"]')" RECORDING "Timeline/Gantt na etapa de gravacao"
check "$(call GET "$LT" "/api/tasks/$T" | j 'd["overdue"]')" True "Tarefa vencida (prazo 15/11) marcada como atrasada"
NID=$(call GET "$CT" /api/notifications | j 'd[0]["id"]')
check "$(curl -s -o /dev/null -w '%{http_code}' -X PATCH "$API/api/notifications/$NID/read" -H "Authorization: Bearer $XT")" 403 "Intruso nao mexe nas notificacoes de Carol"
check "$(code "$XT" "/api/notifications?artistId=$CA")" 403 "Intruso nao le notificacoes de Carol"

echo "== F4: Dashboard"
DASH=$(call GET "$LT" "/api/projects/$P/dashboard")
echo "$DASH" | j '"    conclusao=%s%% atrasadas=%s decisoes=%s\n    tarefas recentes: %s\n    arquivos importantes: %s" % (d["completionPercent"], d["overdueTasks"], d["decisionsRegistered"], [t["title"] for t in d["recentTasks"]], [f["name"] for f in d["importantFiles"]])'
check "$(echo "$DASH" | j 'len(d["recentTasks"])>0 and len(d["weeklyThroughput"])==8')" True "Resumo de tarefas recentes + grafico semanal"
check "$(code "$XT" "/api/projects/$P/dashboard")" 403 "Intruso nao ve dashboard"
echo; echo "Tudo certo."
