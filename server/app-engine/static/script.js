'use strict';

window.addEventListener('load', function () {
  document.getElementById('sign-out').onclick = function () {
    firebase.auth().signOut();
  };

  // FirebaseUI config.
  var uiConfig = {
    signInSuccessUrl: '/',
    signInOptions: [
      // Comment out any lines corresponding to providers you did not check in
      // the Firebase console.
      //firebase.auth.GoogleAuthProvider.PROVIDER_ID,
      firebase.auth.EmailAuthProvider.PROVIDER_ID,
      //firebase.auth.FacebookAuthProvider.PROVIDER_ID,
      //firebase.auth.TwitterAuthProvider.PROVIDER_ID,
      //firebase.auth.GithubAuthProvider.PROVIDER_ID,
      //firebase.auth.PhoneAuthProvider.PROVIDER_ID

    ],
    // Terms of service url.
    tosUrl: '<your-tos-url>'
  };

  firebase.auth().onAuthStateChanged(function (user) {
    console.log("onAuthStateChanged() callback");
    console.log("- user: " + user);

    if (user) {
        document.getElementById('start-stop').style.display = 'inline-block';
        document.getElementById('sign-out').style.display = 'inline-block';
        // User is signed in, so display the "sign out" button and login info.
        console.log(`Signed in as ${user.displayName} (${user.email})`);

        user.getIdToken().then(function (token) {
            // Add the token to the browser's cookies. The server will then be
            // able to verify the token against the API.
            // SECURITY NOTE: As cookies can easily be modified, only put the
            // token (which is verified server-side) in a cookie; do not add other
            // user information.
            console.log("- token: " + token);
            document.cookie = "token=" + token;
            initialize();
        });
    } else {
        // User is signed out.
        // Initialize the FirebaseUI Widget using Firebase.
        var ui = new firebaseui.auth.AuthUI(firebase.auth());
        // Show the Firebase login button.
        ui.start('#firebaseui-auth-container', uiConfig);
        // Update the login state indicators.
        document.getElementById('sign-out').style.display = 'none';
        // Clear the token cookie.
        document.cookie = "token=";
    }
  }, function (error) {
        console.log(error);
        alert('Unable to log in: ' + error)
  });
});
