// Though we have some API info hardcoded here, it doesn't present a security risk. More about this here: https://stackoverflow.com/questions/37482366/is-it-safe-to-expose-firebase-apikey-to-the-public
// TODO: Investigate whether we can remove Firebase entirely

var firebaseConfig = {
    apiKey: "AIzaSyCV8Gmn5kLPp3CIuP5FeZs3iFhF2TMz8Lg",
    authDomain: "lat-long-prototype.firebaseapp.com",
    databaseURL: "https://lat-long-prototype.firebaseio.com",
    projectId: "lat-long-prototype",
    storageBucket: "lat-long-prototype.appspot.com",
    messagingSenderId: "983001193130",
    appId: "1:983001193130:web:a16eedf0da0822582c4ed3",
    measurementId: "G-4V613NSF91"
};

firebase.initializeApp(firebaseConfig);
