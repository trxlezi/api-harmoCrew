"""
Popula a API HarmoCrew com dados realistas para demonstracao.

Cria ~24 musicos com perfil completo (estilos, instrumentos, influencias,
cidade, audio), 5 bandas com requisitos de vaga, candidaturas com status
variados, tarefas em todas as etapas, arquivos versionados, enquete, ensaios
com presenca, metas semanais e mensagens. Os musicos tambem se candidatam aos
projetos que JA existem no banco (ex.: "Banda Demo"), para o ranking ficar cheio.

Pode rodar mais de uma vez: usuarios ja existentes apenas fazem login.

Uso:
    python docs/seed.py                       (API em http://localhost:8080)
    python docs/seed.py http://localhost:8085
Senha de todos os usuarios: secret123   (emails: <nome>@seed.test)
"""
import datetime as dt
import json
import os
import random
import sys
import unicodedata
import urllib.error
import urllib.request
import uuid

API = (sys.argv[1] if len(sys.argv) > 1 else os.environ.get("API", "http://localhost:8080")).rstrip("/")
PASSWORD = "secret123"
HERE = os.path.dirname(os.path.abspath(__file__))
AUDIO_DIR = os.path.join(HERE, "..", "..", "demo-arquivos")
random.seed(42)
TODAY = dt.date.today()


# ------------------------------------------------------------------ HTTP
class ApiError(Exception):
    def __init__(self, status, body):
        super().__init__(f"HTTP {status}: {body[:200]}")
        self.status = status


def call(method, path, token=None, body=None, raw=None, content_type=None):
    headers = {}
    data = None
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if raw is not None:
        data = raw
        headers["Content-Type"] = content_type
    req = urllib.request.Request(API + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            text = resp.read().decode() or "null"
            return json.loads(text)
    except urllib.error.HTTPError as err:
        raise ApiError(err.code, err.read().decode(errors="replace")) from None


def upload(path, token, filename, content, mime, fields=None):
    boundary = uuid.uuid4().hex
    parts = []
    for key, value in (fields or {}).items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
    parts.append(
        f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        f"Content-Type: {mime}\r\n\r\n".encode() + content + b"\r\n"
    )
    parts.append(f"--{boundary}--\r\n".encode())
    return call("POST", path, token, raw=b"".join(parts), content_type=f"multipart/form-data; boundary={boundary}")


def try_call(*args, **kwargs):
    try:
        return call(*args, **kwargs)
    except ApiError as err:
        print(f"    (ignorado: {err})")
        return None


# ------------------------------------------------------------------ dados
MUSICIANS = [
    # nome, especialidade, cidade, estilos, instrumentos, influencias, disponibilidade, bio
    ("Ana Ribeiro", "Baixo", "Sao Paulo", ["MPB", "Samba Rock"], ["Baixo", "Violao"], ["Jorge Ben", "Tim Maia"], "Noites", "Baixista groove, 10 anos de bailes e estudio."),
    ("Bruno Tavares", "Bateria", "Sao Paulo", ["Rock", "Indie"], ["Bateria", "Percussao"], ["Foo Fighters", "Arctic Monkeys"], "Fins de semana", "Baterista de rock alternativo."),
    ("Camila Rocha", "Voz", "Rio de Janeiro", ["MPB", "Bossa Nova"], ["Voz", "Violao"], ["Elis Regina", "Marisa Monte"], "Tardes", "Cantora e compositora."),
    ("Diego Martins", "Guitarra", "Sao Paulo", ["Rock", "Blues"], ["Guitarra", "Violao"], ["Jimi Hendrix", "B.B. King"], "Noites", "Guitarrista de blues e rock."),
    ("Eduarda Lima", "Teclado", "Campinas", ["Jazz", "MPB"], ["Teclado", "Piano"], ["Herbie Hancock", "Tom Jobim"], "Flexivel", "Pianista de jazz com formacao erudita."),
    ("Felipe Souza", "Baixo", "Rio de Janeiro", ["Funk", "Soul"], ["Baixo"], ["Tim Maia", "Stevie Wonder"], "Noites", "Slap e groove setentista."),
    ("Gabriela Nunes", "Violino", "Belo Horizonte", ["Folk", "MPB"], ["Violino", "Viola"], ["Milton Nascimento", "Mumford & Sons"], "Manhas", "Violinista com pegada folk."),
    ("Henrique Alves", "Producao", "Sao Paulo", ["Eletronica", "Pop"], ["Teclado", "Sintetizador"], ["Daft Punk", "Alok"], "Flexivel", "Produtor musical e beatmaker."),
    ("Isabela Costa", "Voz", "Sao Paulo", ["Pop", "R&B"], ["Voz"], ["Beyonce", "IZA"], "Noites", "Vocalista pop com experiencia em palco."),
    ("Joao Pedro", "Bateria", "Campinas", ["Jazz", "Samba"], ["Bateria"], ["Elvin Jones", "Wilson das Neves"], "Fins de semana", "Baterista de jazz e samba."),
    ("Karina Melo", "Saxofone", "Sao Paulo", ["Jazz", "Funk"], ["Saxofone", "Flauta"], ["John Coltrane", "Maceo Parker"], "Noites", "Saxofonista de jazz e funk."),
    ("Lucas Ferreira", "Guitarra", "Curitiba", ["Indie", "Rock"], ["Guitarra"], ["Radiohead", "Los Hermanos"], "Tardes", "Guitarra indie e texturas."),
    ("Mariana Duarte", "Baixo", "Belo Horizonte", ["Rock", "Indie"], ["Baixo"], ["Arctic Monkeys", "Pitty"], "Noites", "Baixista de banda autoral."),
    ("Nicolas Prado", "Percussao", "Salvador", ["Axe", "Samba", "MPB"], ["Percussao", "Bateria"], ["Carlinhos Brown", "Olodum"], "Flexivel", "Percussionista baiano."),
    ("Olivia Santos", "Voz", "Recife", ["Forro", "MPB"], ["Voz", "Sanfona"], ["Luiz Gonzaga", "Elba Ramalho"], "Fins de semana", "Cantora de forro e MPB."),
    ("Paulo Henrique", "Teclado", "Sao Paulo", ["Soul", "Gospel", "Funk"], ["Teclado", "Orgao"], ["Stevie Wonder", "Tim Maia"], "Noites", "Tecladista de soul."),
    ("Rafaela Gomes", "Violao", "Rio de Janeiro", ["Bossa Nova", "MPB"], ["Violao", "Voz"], ["Joao Gilberto", "Tom Jobim"], "Tardes", "Violonista de bossa."),
    ("Samuel Pires", "Trompete", "Sao Paulo", ["Jazz", "Samba Rock"], ["Trompete"], ["Miles Davis", "Jorge Ben"], "Noites", "Trompetista de metais."),
    ("Tatiane Moura", "Bateria", "Rio de Janeiro", ["Funk", "Soul", "Samba Rock"], ["Bateria"], ["Tim Maia", "Jorge Ben"], "Noites", "Baterista groove."),
    ("Victor Hugo", "Guitarra", "Sao Paulo", ["Samba Rock", "MPB", "Funk"], ["Guitarra", "Violao"], ["Jorge Ben", "Tim Maia"], "Fins de semana", "Guitarra suingada."),
    ("Wesley Araujo", "Baixo", "Campinas", ["Jazz", "Fusion"], ["Baixo", "Contrabaixo acustico"], ["Jaco Pastorius", "Arthur Maia"], "Flexivel", "Baixista de fusion."),
    ("Yasmin Freitas", "Voz", "Curitiba", ["Indie", "Folk"], ["Voz", "Ukulele"], ["Norah Jones", "Mallu Magalhaes"], "Manhas", "Voz suave indie folk."),
    ("Zeca Batista", "Sanfona", "Recife", ["Forro", "Xote"], ["Sanfona", "Zabumba"], ["Luiz Gonzaga", "Dominguinhos"], "Fins de semana", "Sanfoneiro raiz."),
    ("Bianca Torres", "Producao", "Sao Paulo", ["Pop", "Eletronica", "R&B"], ["Sintetizador", "Voz"], ["Billie Eilish", "Daft Punk"], "Flexivel", "Produtora e cantora."),
]

BANDS = [
    # lider (indice em MUSICIANS), titulo, estilo, cidade, necessidades, influencias, descricao
    (19, "Suingue Paulista", "Samba Rock", "Sao Paulo", ["Baixo", "Bateria", "Trompete"], ["Jorge Ben", "Tim Maia"], "Banda de samba rock para bailes e festivais."),
    (3, "Estrada de Terra", "Rock", "Sao Paulo", ["Baixo", "Bateria"], ["Foo Fighters", "Arctic Monkeys"], "Rock autoral com shows mensais."),
    (4, "Quarteto Noturno", "Jazz", "Campinas", ["Baixo", "Bateria", "Saxofone"], ["Herbie Hancock", "John Coltrane"], "Jazz instrumental para casas de show."),
    (2, "Bossa do Mar", "Bossa Nova", "Rio de Janeiro", ["Violao", "Percussao"], ["Tom Jobim", "Joao Gilberto"], "Releituras de bossa nova."),
    (14, "Xote Arretado", "Forro", "Recife", ["Sanfona", "Percussao", "Zabumba"], ["Luiz Gonzaga", "Dominguinhos"], "Forro pe-de-serra para festas juninas."),
]


def slug(name):
    plain = unicodedata.normalize("NFD", name).encode("ascii", "ignore").decode()
    return plain.lower().replace(" ", ".")


def norm(value):
    return unicodedata.normalize("NFD", value or "").encode("ascii", "ignore").decode().lower().strip()


def fit(musician, project):
    """Mesma ideia do matching da API, so para escolher a quem se candidatar."""
    _, specialty, city, styles, instruments, influences, *_ = musician
    skills = {norm(i) for i in instruments + [specialty]}
    score = 0
    if norm(project.get("musicalStyle")) in {norm(s) for s in styles}:
        score += 35
    needs = project.get("needs") or []
    if needs:
        score += 30 * len([n for n in needs if norm(n) in skills]) / len(needs)
    wanted = project.get("desiredInfluences") or []
    if wanted:
        score += 20 * len([w for w in wanted if norm(w) in {norm(i) for i in influences}]) / len(wanted)
    if project.get("location") and norm(project["location"]) == norm(city):
        score += 15
    return score


def load_audio():
    clips = []
    for name in ("guia-banda-demo.mp3", "guia-banda-demo-v2.mp3"):
        path = os.path.join(AUDIO_DIR, name)
        if os.path.exists(path):
            with open(path, "rb") as fh:
                clips.append(fh.read())
    if not clips:
        # Sem os arquivos de demo: um MP3 minimo (so cabecalho) ja serve para o player.
        clips.append(b"ID3\x03\x00\x00\x00\x00\x00\x0f" + bytes(64))
    return clips


# ------------------------------------------------------------------ etapas
def ensure_user(musician):
    name, specialty = musician[0], musician[1]
    email = f"{slug(name)}@seed.test"
    try:
        auth = call("POST", "/api/auth/register", body={
            "name": name, "email": email, "password": PASSWORD, "stageName": name, "mainSpecialty": specialty,
        })
        created = True
    except ApiError as err:
        if err.status not in (400, 409):
            raise
        auth = call("POST", "/api/auth/login", body={"email": email, "password": PASSWORD})
        created = False
    return {"token": auth["token"], "artistId": auth["artistId"], "email": email, "data": musician, "created": created}


def fill_profile(user, audio):
    name, specialty, city, styles, instruments, influences, availability, bio = user["data"]
    call("PUT", f"/api/artists/{user['artistId']}", user["token"], {
        "stageName": name, "mainSpecialty": specialty, "city": city, "availability": availability, "bio": bio,
        "musicalStyles": styles, "instruments": instruments, "influences": influences,
    })
    if user["created"] or random.random() < 0.5:
        upload(f"/api/artists/{user['artistId']}/audio", user["token"], f"{slug(name)}.mp3",
               random.choice(audio), "audio/mpeg")


def create_band(leader, band):
    _, title, style, city, needs, influences, description = band
    existing = [p for p in call("GET", "/api/projects", leader["token"]) if p["title"] == title]
    if existing:
        return existing[0], False
    project = call("POST", "/api/projects", leader["token"], {
        "title": title, "description": description, "musicalStyle": style, "status": "ACTIVE",
        "startDate": str(TODAY - dt.timedelta(days=30)), "location": city,
        "needs": needs, "desiredInfluences": influences,
    })
    return project, True


def apply_all(users, projects):
    """Cada musico se candidata aos 3 projetos mais compativeis (inclui projetos antigos)."""
    applications = []
    for user in users:
        ranked = sorted(
            (p for p in projects if user["artistId"] not in {a["id"] for a in p["artists"]}),
            key=lambda p: fit(user["data"], p), reverse=True,
        )
        for project in ranked[:3]:
            if fit(user["data"], project) < 20:
                continue
            try:
                app = call("POST", f"/api/projects/{project['id']}/applications", user["token"], {
                    "artistId": user["artistId"],
                    "message": f"Ola! Sou {user['data'][1].lower()} e curto {', '.join(user['data'][3])}. Topo ensaiar essa semana.",
                    "specialty": user["data"][1],
                    "availability": user["data"][6],
                })
                applications.append((app, project))
            except ApiError as err:
                if "candidatou" not in str(err) and "faz parte" not in str(err):
                    print(f"    candidatura ignorada: {err}")
    return applications


def review(leader_by_project, applications):
    """Lider de cada banda nova avalia parte das candidaturas."""
    approved = {}
    for app, project in applications:
        leader = leader_by_project.get(project["id"])
        if not leader:
            continue  # projeto antigo: deixa pendente para voce avaliar na demo
        roll = random.random()
        if roll < 0.30:
            status, note = "APPROVED", "Bem-vindo(a) a banda! Primeiro ensaio no sabado."
        elif roll < 0.50:
            status, note = "SHORTLISTED", "Gostamos muito do seu audio, vamos marcar um teste."
        elif roll < 0.65:
            status, note = "UNDER_REVIEW", ""
        elif roll < 0.78:
            status, note = "REJECTED", "Obrigado! Buscamos alguem com outro estilo agora."
        else:
            continue
        if try_call("PATCH", f"/api/applications/{app['id']}/status", leader["token"],
                    {"status": status, "evaluationNote": note}) and status == "APPROVED":
            approved.setdefault(project["id"], []).append(app["artistId"])
    return approved


TASKS = [
    ("Definir repertorio", "COMPOSITION", -28, -20, "DONE"),
    ("Compor a ponte da musica 2", "COMPOSITION", -22, 0, "DONE"),
    ("Escrever arranjo de metais", "ARRANGEMENT", -14, 2, "DONE"),
    ("Arranjo de cordas", "ARRANGEMENT", -10, -2, "IN_PROGRESS"),
    ("Gravar guias", "RECORDING", -6, -1, "IN_PROGRESS"),
    ("Gravar bateria", "RECORDING", -2, 6, "TODO"),
    ("Gravar baixo", "RECORDING", 2, 9, "TODO"),
    ("Mixagem do single", "MIXING", 10, 20, "TODO"),
    ("Masterizacao", "MIXING", 20, 25, "TODO"),
    ("Lancar nas plataformas", "RELEASE", 26, 30, "TODO"),
]


def populate_band(leader, project, members_by_id):
    pid = project["id"]
    token = leader["token"]
    project = call("GET", f"/api/projects/{pid}", token)
    members = project["artists"]
    if call("GET", f"/api/projects/{pid}/tasks", token):
        return  # ja populado em execucao anterior

    for title, stage, start, due, status in TASKS:
        assignee = random.choice(members)
        task = call("POST", f"/api/projects/{pid}/tasks", token, {
            "title": title, "stage": stage, "status": "TODO", "priority": random.choice(["LOW", "MEDIUM", "HIGH"]),
            "startDate": str(TODAY + dt.timedelta(days=start)), "dueDate": str(TODAY + dt.timedelta(days=due)),
            "assigneeArtistId": assignee["id"], "responsibleName": assignee["stageName"],
        })
        if status != "TODO":
            call("PATCH", f"/api/tasks/{task['id']}/status", token, {"status": status})

    # Arquivo com 2 versoes + enquete decidida
    audio = load_audio()
    file = upload(f"/api/projects/{pid}/files", token, "guia.mp3", audio[0], "audio/mpeg",
                  {"name": "Guia do single", "category": "AUDIO_GUIDE", "description": "Guia de voz e violao"})
    second = audio[1] if len(audio) > 1 else audio[0] + b"v2"
    file = upload(f"/api/files/{file['id']}/versions", token, "guia-v2.mp3", second, "audio/mpeg",
                  {"note": "Tom abaixado meio tom"})
    upload(f"/api/projects/{pid}/files", token, "setlist.txt",
           "\n".join(t[0] for t in TASKS[:5]).encode(), "text/plain",
           {"name": "Setlist do show", "category": "SETLIST"})
    versions = sorted(file["versions"], key=lambda v: v["versionNumber"])
    poll = call("POST", f"/api/projects/{pid}/polls", token, {
        "question": "Qual guia usamos na gravacao?",
        "options": [{"label": f"v{v['versionNumber']}", "fileVersionId": v["id"]} for v in versions],
    })
    call("POST", f"/api/polls/{poll['id']}/votes", token, {"optionId": poll["options"][-1]["id"]})
    for member in members:
        voter = members_by_id.get(member["id"])
        if voter and voter is not leader:
            try_call("POST", f"/api/polls/{poll['id']}/votes", voter["token"],
                     {"optionId": random.choice(poll["options"])["id"]})
    call("POST", f"/api/polls/{poll['id']}/close", token, {"tieBreakOptionId": poll["options"][-1]["id"]})
    call("POST", f"/api/projects/{pid}/decisions", token, {
        "title": "Shows apenas aos sabados", "description": "Agenda combinada em reuniao.",
        "impact": "Ensaios passam para quinta", "decidedAt": str(TODAY - dt.timedelta(days=7)),
        "decidedByArtistId": leader["artistId"],
    })

    # Ensaios: 3 realizados (com presenca) + 1 agendado
    ids = [m["id"] for m in members]
    for weeks in (3, 2, 1):
        rehearsal = call("POST", f"/api/projects/{pid}/rehearsals", token, {
            "title": f"Ensaio semana -{weeks}", "date": str(TODAY - dt.timedelta(weeks=weeks)),
            "time": "19:00:00", "location": "Estudio Centro", "status": "SCHEDULED",
        })
        present = [i for i in ids if random.random() < 0.8] or ids[:1]
        call("PATCH", f"/api/rehearsals/{rehearsal['id']}/attendance", token, {"attendedArtistIds": present})
    call("POST", f"/api/projects/{pid}/rehearsals", token, {
        "title": "Ensaio geral", "date": str(TODAY + dt.timedelta(days=3)), "time": "20:00:00",
        "location": "Estudio Centro", "status": "SCHEDULED",
    })

    # Metas semanais
    week = f"{TODAY.isocalendar()[0]}-S{TODAY.isocalendar()[1]:02d}"
    for member, title, status in zip(members, ["Fechar arranjo", "Estudar repertorio", "Divulgar show"],
                                     ["DONE", "IN_PROGRESS", "PLANNED"]):
        goal = call("POST", f"/api/projects/{pid}/weekly-goals", token, {
            "title": title, "ownerArtistId": member["id"], "weekLabel": week,
            "dueDate": str(TODAY + dt.timedelta(days=4)), "status": "PLANNED",
        })
        if status != "PLANNED":
            call("PATCH", f"/api/weekly-goals/{goal['id']}/status", token, {"status": status})

    # Mensagens
    for member in members[:3]:
        author = members_by_id.get(member["id"])
        if author:
            try_call("POST", f"/api/projects/{pid}/messages", author["token"], {
                "senderArtistId": author["artistId"], "type": "MESSAGE",
                "content": random.choice(["Bora ensaiar quinta?", "Subi a guia nova!", "Curti a v2", "Fechado!"]),
            })


def main():
    print(f"API: {API}")
    call("GET", "/health")
    audio = load_audio()

    print(f"1/5 Musicos ({len(MUSICIANS)})")
    users = []
    for musician in MUSICIANS:
        user = ensure_user(musician)
        fill_profile(user, audio)
        users.append(user)
        print(f"    {'+' if user['created'] else '='} {musician[0]:<16} {user['email']}")
    by_artist = {u["artistId"]: u for u in users}

    print(f"2/5 Bandas ({len(BANDS)})")
    leader_by_project = {}
    new_projects = []
    for band in BANDS:
        leader = users[band[0]]
        project, created = create_band(leader, band)
        leader_by_project[project["id"]] = leader
        new_projects.append(project)
        print(f"    {'+' if created else '='} {band[1]} (lider: {leader['data'][0]})")

    print("3/5 Candidaturas (bandas novas + projetos que ja existiam)")
    all_projects = call("GET", "/api/projects", users[0]["token"])
    applications = apply_all(users, all_projects)
    print(f"    {len(applications)} candidaturas enviadas")

    print("4/5 Avaliacao das candidaturas pelos lideres")
    approved = review(leader_by_project, applications)
    print(f"    {sum(len(v) for v in approved.values())} aprovados")

    print("5/5 Tarefas, arquivos, enquetes, ensaios, metas e mensagens")
    for project in new_projects:
        populate_band(leader_by_project[project["id"]], project, by_artist)
        print(f"    {project['title']}")

    print("\nPronto! Logins (senha secret123):")
    for band in BANDS:
        print(f"    {users[band[0]]['email']:<28} lider de {band[1]}")
    print("    ana.ribeiro@seed.test        musica candidata em varias bandas")


if __name__ == "__main__":
    try:
        main()
    except ApiError as err:
        sys.exit(f"Erro da API: {err}")
    except urllib.error.URLError as err:
        sys.exit(f"Nao consegui falar com a API em {API}: {err.reason}. Ela esta rodando?")
