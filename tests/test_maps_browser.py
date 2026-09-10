"""Exercise the real static UI with deterministic Google SDK and HTTP test doubles.
This checks integration behavior, not Google's live authorization or data availability.
"""
import base64
import json
import mimetypes
import os
from pathlib import Path
import unittest
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
PIXEL = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aZAAAAABJRU5ErkJggg==')


class Api:
    def __init__(self, enabled=True):
        self.enabled = enabled
        self.status = 'OPEN'
        self.suggestions = []
        self.members = [{'id': 1, 'memberName': 'Host'}]
        self.votes = {}
        self.posts = []
        self.fail_get = False
        self.winner = None

    def route(self, route):
        request = route.request
        path = request.url.split('/api/', 1)[1]
        data = request.post_data_json if request.method == 'POST' and request.post_data else {}

        def send(body=None, status=200):
            route.fulfill(status=status, content_type='application/json', body=json.dumps(body) if body is not None else '')

        if request.method == 'GET':
            if path == 'config/maps':
                return send({'enabled': self.enabled, 'browserKey': 'TEST_BROWSER_KEY' if self.enabled else '', 'mapId': 'DEMO_MAP_ID'})
            if self.fail_get:
                return send({'message': 'Test outage'}, 503)
            if path.startswith('parties/code/'):
                return send({'id': 1, 'name': 'Dinner', 'status': self.status, 'joinCode': 'ABC123', 'members': self.members})
            if path == 'parties/1/suggestions':
                return send(self.suggestions)
            if path == 'parties/1/voting':
                return send({'partyId': 1, 'status': self.status, 'winnerSuggestionId': self.winner,
                             'winnerSuggestionName': None, 'counts': self.counts() if self.status == 'FINALIZED' else {}})
        self.posts.append((path, data))
        if path == 'parties':
            return send({'partyId': 1, 'joinCode': 'ABC123', 'memberId': 1, 'memberToken': 'host-member', 'hostToken': 'host'}, 201)
        if path == 'parties/join':
            self.members.append({'id': 2, 'memberName': data['memberName']})
            return send({'partyId': 1, 'joinCode': 'ABC123', 'memberId': 2, 'memberToken': 'guest-member'}, 201)
        if path == 'parties/1/suggestions':
            if self.status != 'OPEN':
                return send({'message': 'Suggestions are closed'}, 400)
            if data.get('googlePlaceId') and any(s.get('googlePlaceId') == data['googlePlaceId'] for s in self.suggestions):
                return send({'message': 'Duplicate'}, 409)
            suggestion = {'id': len(self.suggestions) + 1, 'memberId': 1, 'memberName': 'Host',
                          'name': data.get('name'), 'googlePlaceId': data.get('googlePlaceId')}
            self.suggestions.append(suggestion)
            return send(suggestion, 201)
        if path == 'parties/1/voting/start':
            self.status = 'VOTING'
            return send(status=204)
        if path == 'parties/1/voting/votes':
            token = request.headers.get('x-member-token')
            if token in self.votes:
                return send({'message': 'Already voted'}, 409)
            self.votes[token] = data['suggestionId']
            if len(self.votes) == len(self.members):
                self.finalize()
            return send(status=201)
        if path == 'parties/1/voting/finalize':
            self.finalize()
            return send(status=204)
        return send({'message': 'Unknown test endpoint'}, 404)

    def counts(self):
        return {str(id): list(self.votes.values()).count(id) for id in set(self.votes.values())}

    def finalize(self):
        self.status = 'FINALIZED'
        self.winner = int(max(self.counts(), key=lambda key: self.counts()[key]))

    def seed(self, *ids):
        self.suggestions = [{'id': n, 'memberId': 1, 'memberName': 'Host', 'name': None, 'googlePlaceId': id}
                            for n, id in enumerate(ids, 1)]


class MapsBrowserTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.origin = 'https://semafork.test'
        cls.pw = sync_playwright().start()
        cls.browser = cls.pw.chromium.launch(headless=True, executable_path=os.getenv('PLAYWRIGHT_CHROMIUM_EXECUTABLE'))

    @classmethod
    def tearDownClass(cls):
        cls.browser.close()
        cls.pw.stop()

    def setUp(self):
        self.contexts = []
        self.api = Api()
        self.errors = []

    def tearDown(self):
        # Rendered evidence, explicitly labeled as using a Google test double.
        output = ROOT / 'test-results'
        output.mkdir(exist_ok=True)
        for index, context in enumerate(self.contexts):
            for page in context.pages:
                try:
                    page.screenshot(path=str(output / f'{self._testMethodName}-{index}.png'), full_page=True)
                except Exception:
                    pass
            context.close()
        self.assertEqual([], self.errors, 'Uncaught browser errors')

    def page(self, host=True, width=1280, home=False, sdk=True):
        context = self.browser.new_context(viewport={'width': width, 'height': 900})
        self.contexts.append(context)
        if sdk:
            context.add_init_script(path=str(ROOT / 'tests/fake-google.js'))
        else:
            context.route('https://maps.googleapis.com/maps/api/js?**', lambda route: route.abort('failed'))
        if host:
            context.add_init_script("localStorage.setItem('semafork.session.ABC123', JSON.stringify({partyId:1,memberId:1,memberToken:'host-member',hostToken:'host',voted:false}));")

        def static(route):
            name = urlparse(route.request.url).path.lstrip('/') or 'index.html'
            path = ROOT / 'src/main/resources/static' / name
            if path.is_file():
                route.fulfill(content_type=mimetypes.guess_type(name)[0] or 'text/plain', body=path.read_bytes())
            else:
                route.fulfill(status=404, body='Not found')

        context.route(self.origin + '/**', static)
        context.route(self.origin + '/api/**', self.api.route)
        context.route('https://maps.googleapis.com/mock-photo-*.png', lambda route: route.fulfill(content_type='image/png', body=PIXEL))
        page = context.new_page()
        page.on('pageerror', lambda error: self.errors.append(str(error)))
        page.goto(self.origin + ('/' if home else '/?party=ABC123'))
        if home:
            expect(page.locator('#create-form')).to_be_visible()
        else:
            expect(page.locator('#party-name')).to_have_text('Dinner')
        return page

    def choose(self, page, id, poi=False):
        page.wait_for_function('window.__maps.autocomplete !== undefined')
        if poi:
            page.evaluate("id => window.__maps.map.events.click({placeId:id,stop(){window.__maps.poiStopped=true;}})", id)
        else:
            page.evaluate('id => window.__maps.autocomplete.choose(id)', id)
        expect(page.locator('#place-selection [data-add-place]')).to_be_visible()

    def add(self, page, id, poi=False):
        self.choose(page, id, poi)
        page.locator('#place-selection [data-add-place]').click()
        expect(page.locator('#place-selection [data-add-place]')).to_have_text('Already a candidate')

    def test_two_browsers_pick_compare_vote_and_keep_every_marker(self):
        host = self.page()
        guest = self.page(host=False)
        guest.locator('#join-current-form input').fill('Guest')
        guest.locator('#join-current-form button').click()
        expect(guest.locator('#join-current')).to_be_hidden()
        self.add(host, 'place_a')
        expect(host.locator('#candidate-1')).to_contain_text('Cava Downtown')
        expect(host.locator('#candidate-1')).to_contain_text('321 ratings')
        expect(host.locator('#candidate-1 .price-level')).to_have_text('$$')
        expect(host.locator('#candidate-1')).to_contain_text('Photographer')
        self.add(host, 'place_b', poi=True)
        self.assertTrue(host.evaluate('window.__maps.poiStopped'))
        expect(host.locator('#candidate-2')).to_contain_text('Price unavailable')
        expect(host.locator('#candidate-2')).to_contain_text('No photo available')
        expect(guest.locator('.candidate-card')).to_have_count(2, timeout=10000)
        expect(guest.locator('#candidate-map fake-map-marker')).to_have_count(2)
        host.locator('#candidate-1 .candidate-details').click()
        host.locator('#place-selection summary').click()
        expect(host.locator('.review-content')).to_contain_text('Reviewer')
        expect(host.locator('.review-content')).to_contain_text('<img src=x')
        self.assertIsNone(host.evaluate('window.__pwned'))
        self.assertEqual(0, host.locator('.place-review img').count())
        payloads = [data for path, data in self.api.posts if path.endswith('/suggestions')]
        self.assertEqual({'googlePlaceId': 'place_a'}, payloads[0])
        host.locator('#start-voting').click()
        expect(guest.locator('#phase-label')).to_have_text('Voting', timeout=10000)
        guest.locator('#candidate-1 .candidate-vote').click()
        expect(guest.locator('#vote-notice')).to_contain_text('Your vote is in')
        expect(guest.locator('.candidate-card')).to_have_count(2)
        expect(guest.locator('#candidate-map fake-map-marker')).to_have_count(2)
        guest.locator('#candidate-2 .candidate-details').click()
        expect(guest.locator('#place-selection')).to_contain_text('Cava Uptown')
        host.locator('#candidate-1 .candidate-vote').click()
        expect(host.locator('#winner-name')).to_have_text('Cava Downtown')
        expect(guest.locator('#phase-label')).to_have_text('Decided', timeout=10000)
        expect(guest.locator('#candidate-1 .vote-count')).to_have_text('2 votes')
        guest.reload()
        expect(guest.locator('#winner-name')).to_have_text('Cava Downtown')
        expect(guest.locator('#candidate-map fake-map-marker')).to_have_count(2)

    def test_home_create_and_join_forms_keep_working(self):
        host = self.page(host=False, home=True)
        host.locator('#create-form input[name=name]').fill('Dinner')
        host.locator('#create-form input[name=hostName]').fill('Host')
        host.locator('#create-form button').click()
        expect(host.locator('#party-name')).to_have_text('Dinner')
        self.assertEqual('host', host.evaluate("JSON.parse(localStorage.getItem('semafork.session.ABC123')).hostToken"))
        guest = self.page(host=False, home=True)
        guest.locator('#join-form input[name=joinCode]').fill('ABC123')
        guest.locator('#join-form input[name=memberName]').fill('Guest')
        guest.locator('#join-form button').click()
        expect(guest.locator('#party-name')).to_have_text('Dinner')
        expect(guest.locator('#join-current')).to_be_hidden()
        expect(guest.locator('#host-controls')).to_be_hidden()
        self.assertEqual('guest-member', guest.evaluate("JSON.parse(localStorage.getItem('semafork.session.ABC123')).memberToken"))

    def test_google_script_failure_isolated_from_party_flow(self):
        page = self.page(sdk=False)
        expect(page.locator('#map-message')).to_contain_text('could not be reached', timeout=10000)
        page.locator('#suggestion-form input').fill('Offline alternative')
        page.locator('#suggestion-form button').click()
        expect(page.locator('#candidate-1')).to_contain_text('Offline alternative')
        expect(page.locator('#candidate-map')).to_be_hidden()

    def test_polling_preserves_map_search_selection_and_request_counts(self):
        self.api.seed('place_a', 'place_b')
        page = self.page()
        expect(page.locator('#candidate-map fake-map-marker')).to_have_count(2)
        page.locator('gmp-place-autocomplete input').fill('Unsubmitted restaurant query')
        page.evaluate('window.__maps.map.panTo({lat:12,lng:34})')
        calls = page.evaluate('JSON.stringify(window.__maps.calls)')
        page.wait_for_timeout(6500)
        self.assertEqual(1, page.evaluate('window.__maps.count'))
        self.assertEqual(calls, page.evaluate('JSON.stringify(window.__maps.calls)'))
        self.assertEqual({'lat': 12, 'lng': 34}, page.evaluate('window.__maps.map.center'))
        expect(page.locator('gmp-place-autocomplete input')).to_have_value('Unsubmitted restaurant query')
        self.api.fail_get = True
        expect(page.locator('#network-warning')).to_be_visible(timeout=10000)
        self.api.fail_get = False
        expect(page.locator('#network-warning')).to_be_hidden(timeout=10000)
        self.assertEqual(1, page.evaluate('window.__maps.count'))
        expect(page.locator('gmp-place-autocomplete input')).to_have_value('Unsubmitted restaurant query')

    def test_missing_key_does_not_block_manual_suggestions_or_voting(self):
        self.api.enabled = False
        page = self.page()
        expect(page.locator('#map-message')).to_contain_text('not configured')
        self.assertEqual(0, page.evaluate('window.__maps.count'))
        for name in ['First restaurant', 'Second restaurant']:
            page.locator('#suggestion-form input').fill(name)
            page.locator('#suggestion-form button').click()
            expect(page.locator('#suggestion-form input')).to_have_value('')
        expect(page.locator('.candidate-card')).to_have_count(2)
        page.locator('#start-voting').click()
        page.locator('#candidate-1 .candidate-vote').click()
        expect(page.locator('#winner-name')).to_have_text('First restaurant')

    def test_failed_lookup_retry_rapid_selection_and_geolocation_denial(self):
        self.api.seed('place_a', 'place_b')
        page = self.page(width=390)
        expect(page.locator('#candidate-map fake-map-marker')).to_have_count(2)
        page.evaluate("window.__maps.delays.place_slow = 300; window.__maps.autocomplete.choose('place_slow'); window.__maps.autocomplete.choose('place_fast');")
        expect(page.locator('#place-selection')).to_contain_text('Fast Noodles')
        page.wait_for_timeout(400)
        expect(page.locator('#place-selection')).not_to_contain_text('Slow Sushi')
        page.locator('#place-selection [data-add-place]').click()
        expect(page.locator('#candidate-3')).to_contain_text('Fast Noodles')
        self.assertEqual('place_fast', self.api.suggestions[-1]['googlePlaceId'])
        page.evaluate("window.__maps.failures.place_retry = true; window.__maps.autocomplete.choose('place_retry')")
        expect(page.locator('#place-selection')).to_contain_text('Could not load')
        page.evaluate("window.__maps.failures.place_retry = false; window.__maps.autocomplete.choose('place_retry')")
        expect(page.locator('#place-selection')).to_contain_text('Retry Cafe')
        page.evaluate("() => { navigator.geolocation.getCurrentPosition = (ok, fail) => fail({code:1}); }")
        page.locator('#map-locate').click()
        expect(page.locator('#map-message')).to_contain_text('denied or unavailable')
        self.assertTrue(page.evaluate('document.documentElement.scrollWidth <= innerWidth'))


if __name__ == '__main__':
    unittest.main()
