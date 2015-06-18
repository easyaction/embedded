import webapp2
import json
from google.appengine.api import memcache

class StatusPage(webapp2.RequestHandler):
    def get(self):
        self.response.headers['Content-Type'] = 'text/plain'
        self.response.write('locked' if memcache.get('lock') else 'unlocked')

class LockPage(webapp2.RequestHandler):
    def post(self):
        memcache.set(key='lock', value=True)
        self.response.headers['Content-Type'] = 'text/plain'
        self.response.write('locked')

class UnlockPage(webapp2.RequestHandler):
    def post(self):
        memcache.set(key='lock', value=False)
        self.response.headers['Content-Type'] = 'application/json'
        self.response.write('unlocked')

app = webapp2.WSGIApplication([
    ('/status', StatusPage),
    ('/lock', LockPage),
    ('/unlock', UnlockPage),
], debug=True)
