from __future__ import annotations

from bs4 import BeautifulSoup

from backend.services.esprit_scraper import (
    _build_login_payload,
    _build_student_next_payload,
    _build_student_robot_payload,
    _extract_bookings_from_html,
    _extract_pdf_links,
    _extract_pdf_postbacks,
    _has_login_error,
)


def test_build_login_payload_includes_hidden_and_student_id() -> None:
    html = """
    <html><body>
      <form action="default.aspx" method="post">
        <input type="hidden" name="__VIEWSTATE" value="abc123" />
        <input type="text" name="ctl00$Main$txtIdentifiant" />
        <input type="password" name="ctl00$Main$txtPassword" />
        <input type="submit" name="ctl00$Main$btnSubmit" value="Se Connecter" />
      </form>
    </body></html>
    """
    soup = BeautifulSoup(html, "html.parser")

    payload = _build_login_payload(
        soup,
        student_id="22B1234",
        password="secret",
        captcha=None,
        extra_fields={"custom_field": "custom_value"},
    )

    assert payload["__VIEWSTATE"] == "abc123"
    assert payload["ctl00$Main$txtIdentifiant"] == "22B1234"
    assert payload["ctl00$Main$txtPassword"] == "secret"
    assert payload["custom_field"] == "custom_value"


def test_login_error_detection_ignores_hidden_aspnet_validators() -> None:
    html = """
    <html><body>
      <span id="ContentPlaceHolder1_RequiredFieldValidator3" style="visibility:hidden;">Cin incorrect</span>
      <span id="ContentPlaceHolder1_RequiredFieldValidator7" style="display:none;">Mot de passe incorrect</span>
    </body></html>
    """
    soup = BeautifulSoup(html, "html.parser")

    assert _has_login_error(soup) is False


def test_login_error_detection_reads_alerts() -> None:
    html = "<html><body><script>alert('Identifiant incorrect ! ')</script></body></html>"
    soup = BeautifulSoup(html, "html.parser")

    assert _has_login_error(soup) is True


def test_extract_bookings_from_html_table_row() -> None:
    html = """
    <table>
      <tr>
        <td>Lundi 27/04/2026</td>
        <td>Programming Fundamentals A42 09:00-10:30</td>
      </tr>
      <tr>
        <td>Mercredi 29/04/2026</td>
        <td>Databases B205 10h45-12h15</td>
      </tr>
    </table>
    """
    soup = BeautifulSoup(html, "html.parser")

    bookings = _extract_bookings_from_html(soup, group_name="2A1")

    assert len(bookings) == 2
    assert bookings[0]["group_name"] == "2A1"
    assert bookings[0]["room_name"] == "A42"
    assert bookings[0]["start_time"] == "09:00"
    assert bookings[0]["end_time"] == "10:30"
    assert bookings[1]["room_name"] == "B205"
    assert bookings[1]["start_time"] == "10:45"
    assert bookings[1]["end_time"] == "12:15"


def test_build_student_payload_uses_esprit_student_fields_only() -> None:
    html = """
    <html><body>
      <form action="default.aspx" method="post">
        <input type="hidden" name="__VIEWSTATE" value="abc123" />
        <input type="text" name="ctl00$ContentPlaceHolder1$TextBox1" />
        <input type="password" name="ctl00$ContentPlaceHolder1$TextBox6" />
        <input type="text" name="ctl00$ContentPlaceHolder1$TextBox3" id="ContentPlaceHolder1_TextBox3" />
        <input type="checkbox" name="ctl00$ContentPlaceHolder1$chkImage" id="ContentPlaceHolder1_chkImage" checked="checked" />
        <input type="submit" name="ctl00$ContentPlaceHolder1$Button3" value="Suivant" />
      </form>
    </body></html>
    """
    soup = BeautifulSoup(html, "html.parser")

    payload = _build_student_next_payload(soup, student_id="22B1234", password="secret")

    assert payload["ctl00$ContentPlaceHolder1$TextBox3"] == "22B1234"
    assert payload["ctl00$ContentPlaceHolder1$chkImage"] == "on"
    assert payload["ctl00$ContentPlaceHolder1$Button3"] == "Suivant"
    assert payload["ctl00$ContentPlaceHolder1$TextBox1"] == ""
    assert payload["ctl00$ContentPlaceHolder1$TextBox6"] == ""


def test_build_student_robot_payload_posts_checkbox_event() -> None:
    html = """
    <html><body>
      <form action="default.aspx" method="post">
        <input type="hidden" name="__VIEWSTATE" value="abc123" />
        <input type="text" name="ctl00$ContentPlaceHolder1$TextBox3" id="ContentPlaceHolder1_TextBox3" />
        <input type="checkbox" name="ctl00$ContentPlaceHolder1$chkImage" id="ContentPlaceHolder1_chkImage" />
      </form>
    </body></html>
    """
    soup = BeautifulSoup(html, "html.parser")

    payload = _build_student_robot_payload(soup, student_id="22B1234")

    assert payload["ctl00$ContentPlaceHolder1$TextBox3"] == "22B1234"
    assert payload["ctl00$ContentPlaceHolder1$chkImage"] == "on"
    assert payload["__EVENTTARGET"] == "ctl00$ContentPlaceHolder1$chkImage"


def test_build_student_password_payload_uses_etudiant_login_button() -> None:
    html = """
    <html><body>
      <form action="default.aspx" method="post">
        <input type="hidden" name="__VIEWSTATE" value="abc123" />
        <input type="password" name="ctl00$ContentPlaceHolder1$TextBox7" id="ContentPlaceHolder1_TextBox7" />
        <input type="submit" name="ctl00$ContentPlaceHolder1$ButtonEtudiant" value="Connexion" />
      </form>
    </body></html>
    """
    soup = BeautifulSoup(html, "html.parser")

    payload = _build_login_payload(
        soup,
        student_id="22B1234",
        password="secret",
        captcha=None,
        extra_fields=None,
    )

    assert payload["ctl00$ContentPlaceHolder1$TextBox7"] == "secret"
    assert payload["ctl00$ContentPlaceHolder1$ButtonEtudiant"] == "Connexion"
    assert "ctl00$ContentPlaceHolder1$Button3" not in payload
    assert "ctl00$ContentPlaceHolder1$TextBox3" not in payload


def test_extract_pdf_links_ignores_javascript_postbacks() -> None:
    html = """
    <table>
      <tr>
        <td>EDT des classes CJ_27 avril.pdf</td>
        <td><a href="javascript:__doPostBack('ctl00$Grid$ctl02$lnkDownload','')">Télécharger</a></td>
      </tr>
      <tr>
        <td><a href="/files/emploi.pdf">Direct PDF</a></td>
      </tr>
    </table>
    """
    soup = BeautifulSoup(html, "html.parser")

    links = _extract_pdf_links(soup, "https://esprit-tn.com/esponline/Etudiants/Emplois.aspx")

    assert links == ["https://esprit-tn.com/files/emploi.pdf"]


def test_extract_pdf_postbacks_finds_gridview_download() -> None:
    html = """
    <table>
      <tr>
        <td>EDT des classes CJ_27 avril.pdf</td>
        <td><a href="javascript:__doPostBack('ctl00$ContentPlaceHolder1$GridView1$ctl02$lnkDownload','')">Télécharger</a></td>
        <td><a href="javascript:__doPostBack('ctl00$ContentPlaceHolder1$GridView1$ctl02$lnkBrowse','')">Apercu</a></td>
      </tr>
    </table>
    """
    soup = BeautifulSoup(html, "html.parser")

    assert _extract_pdf_postbacks(soup) == [
        ("ctl00$ContentPlaceHolder1$GridView1$ctl02$lnkDownload", "")
    ]


def test_extract_bookings_from_matrix_table_with_slot_headers() -> None:
    html = """
    <table>
      <tr>
        <th>Jour</th>
        <th>09H:00</th>
        <th>10H:30</th>
        <th>10H:45</th>
        <th>12H:15</th>
      </tr>
      <tr>
        <td>Lundi 27/04/2026</td>
        <td colspan="2">Mathématiques de Base 1 / A13</td>
        <td colspan="2">Programmation procédurale 1 / B205</td>
      </tr>
    </table>
    """
    soup = BeautifulSoup(html, "html.parser")

    bookings = _extract_bookings_from_html(soup, group_name="2A1")

    assert len(bookings) == 2
    assert bookings[0]["course_name"] == "Mathématiques de Base 1"
    assert bookings[0]["room_name"] == "A13"
    assert bookings[0]["start_time"] == "09:00"
    assert bookings[0]["end_time"] == "10:30"
    assert bookings[1]["course_name"] == "Programmation procédurale 1"
    assert bookings[1]["room_name"] == "B205"
    assert bookings[1]["start_time"] == "10:45"
    assert bookings[1]["end_time"] == "12:15"


def test_extract_bookings_from_structured_row_with_separate_times() -> None:
    html = """
    <table>
      <tr>
        <th>Date</th>
        <th>Début</th>
        <th>Fin</th>
        <th>Matière</th>
        <th>Salle</th>
      </tr>
      <tr>
        <td>Mardi 28/04/2026</td>
        <td>13H:30</td>
        <td>15H:00</td>
        <td>Systèmes d'exploitation</td>
        <td>C204</td>
      </tr>
    </table>
    """
    soup = BeautifulSoup(html, "html.parser")

    bookings = _extract_bookings_from_html(soup, group_name="3A2")

    assert len(bookings) == 1
    assert bookings[0]["room_name"] == "C204"
    assert bookings[0]["course_name"] == "Systèmes d'exploitation"
    assert bookings[0]["start_time"] == "13:30"
    assert bookings[0]["end_time"] == "15:00"
