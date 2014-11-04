// -*- Mode: JavaScript; tab-width: 2; indent-tabs-mode: nil; -*-
// vim:set ft=javascript ts=2 sw=2 sts=2 cindent:
var Ajax = (function($, window, undefined) {
    var Ajax = function(dispatcher) {
      var that = this;
      var pending = 0;
      var count = 0;
      var pendingList = {};

      // merge data will get merged into the response data
      // before calling the callback
      var ajaxCall = function(data, callback, merge) {
        merge = merge || {};
        dispatcher.post('spin');
        pending++;
        var id = count++;

        // special value: `merge.keep = true` prevents obsolescence
        pendingList[id] = merge.keep || false;
        delete merge.keep;

        // If no protocol version is explicitly set, set it to current
        if (data['protocol'] === undefined) {
          // TODO: Extract the protocol version somewhere global
          data['protocol'] = 1;
        }

// WEBANNO EXTENSION BEGIN
        wicketAjaxPost(
          dispatcher.ajaxUrl, 
          $.param(data), 
          // success
          function() {
            var response = Wicket.$(dispatcher.wicketId).temp;
            delete Wicket.$(dispatcher.wicketId).temp;
            if (response === undefined) {
                console.log('Server response did not contain brat data - ignoring');
            	// This is likely a wicket ajax-redirect and nothing that relates to brat.
            	// We simply ignore this.
                return;
            }
// WEBANNO EXTENSION END
            pending--;
            // If no exception is set, verify the server results
            if (response.exception == undefined && response.action !== data.action) {
              console.error('Action ' + data.action +
                ' returned the results of action ' + response.action);
              response.exception = true;
              dispatcher.post('messages', [[['Protocol error: Action' + data.action + ' returned the results of action ' + response.action + ' maybe the server is unable to run, please run tools/troubleshooting.sh from your installation to diagnose it', 'error', -1]]]);
            }

            // If the request is obsolete, do nothing; if not...
            if (pendingList.hasOwnProperty(id)) {
              dispatcher.post('messages', [response.messages]);
              if (response.exception == 'configurationError'
                  || response.exception == 'protocolVersionMismatch') {
                // this is a no-rescue critical failure.
                // Stop *everything*.
                pendingList = {};
                dispatcher.post('screamingHalt');
                // If we had a protocol mismatch, prompt the user for a reload
                if (response.exception == 'protocolVersionMismatch') {
                  if(confirm('The server is running a different version ' +
                      'from brat than your client, possibly due to a ' +
                      'server upgrade. Would you like to reload the ' +
                      'current page to update your client to the latest ' +
                      'version?')) {
                    window.location.reload(true);
                  } else {
                    dispatcher.post('messages', [[['Fatal Error: Protocol ' +
                        'version mismatch, please contact the administrator',
                        'error', -1]]]);
                  }
                }
                return;
              }

              delete pendingList[id];

              // if .exception is just Boolean true, do not process
              // the callback; if it is anything else, the
              // callback is responsible for handling it
              if (response.exception == true) {
                $('#waiter').dialog('close');
              } else if (callback) {
                $.extend(response, merge);
                dispatcher.post(0, callback, [response]);
              }
            }
            dispatcher.post('unspin');
// WEBANNO EXTENSION BEGIN
          }.bind(this),
          // error
          function() {
            pending--;
            dispatcher.post('unspin');
            $('#waiter').dialog('close');
            // TODO find some way or access or pass on the response, textStatus and errorThrown from BratAnnotator or Wicket.
            // In the original ajax.js, these are parameters to the error callback.
//            dispatcher.post('messages', [[['Error: Action' + data.action + ' failed on error ' + response.statusText, 'error']]]);
//            console.error(textStatus + ':', errorThrown, response);
          }.bind(this),
          null, 
          null);
// WEBANNO EXTENSION END
        return id;
      };

      var isReloadOkay = function() {
        // do not reload while data is pending
        return pending == 0;
      };

      var makeObsolete = function(all) {
        if (all) {
          pendingList = {};
        } else {
          $.each(pendingList, function(id, keep) {
            if (!keep) delete pendingList[id];
          });
        }
      }

      dispatcher.
          on('isReloadOkay', isReloadOkay).
          on('makeAjaxObsolete', makeObsolete).
          on('ajax', ajaxCall);
    };

    return Ajax;
})(jQuery, window);
