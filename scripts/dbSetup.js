// dbSetup.js
//
// Script for initial Sentinel MongoDB database setup.
//
// This script creates the required indices and a mock development user.
//
// The script must be run by an existing MongoDB user that can create
// databases and add users. Since this is part of an Ansible deployment,
// you are *strongly* recommended to keep this script idempotent.
//
// Usage: mongo {address}:{port} dbSetup.js

// Helper function for checking array equality
var arrayEquals = function(arr1, arr2) {
  if (arr1.length !== arr2.length) return false;
  for (var i = 0, len = arr1.length; i < len; i++) {
    if (arr1[i] !== arr2[i]) {
      return false;
    }
  }
  return true;
};

// Helper function for checking expected keys in an index
var missingIndex = function(existingIndices, expectedIndex) {
  var exp = Object.keys(expectedIndex);
  exp.sort();
  return !existingIndices.find(function(item) {
    var obs = Object.keys(item.key);
    obs.sort();
    return arrayEquals(exp, obs);
  });
};

// fs.files -> index by md5 and metadata.uploaderId + unique
var fileIndex = {"md5": 1, "metadata.uploaderId": 1};
if (missingIndex(db.fs.files.getIndexes(), fileIndex)) {
  db.fs.files.createIndex(fileIndex, {"unique": true});
  print("index created: fs.files");
}

// annotations -> annotMd5 + unique
var annotIndex = {"annotMd5": 1};
if (missingIndex(db.annotations.getIndexes(), annotIndex)) {
  db.annotations.createIndex(annotIndex, {"unique": true});
  print("index created: annotations");
}

// references -> combinedMd5 + unique
var refIndex = {"combinedMd5": 1};
if (missingIndex(db.references.getIndexes(), refIndex)) {
  db.references.createIndex(refIndex, {"unique": true});
  print("index created: reference");
}

// NOTE: must be kept in sync with User in the source code
// TODO: store this in as ansible variables
var devUser = {
    id: "dev",
    email: "dev@sentinel.org",
    // log2 10 hashing round of `dev`
    hashedPassword: "$2a$10$dNNzi9ieIj1Lk/ED184tPOHJeYDCIc/9bvCJUggC8Gl.4d4pEsdn6",
    activeKey: "dev",
    verified: true,
    isAdmin: true,
    creationTimeUtc: new Date()
}
var query = Object.assign({}, devUser);
delete query.creationTimeUtc;
var existingUserCount = db.users.find(query).count();
if (existingUserCount === 0) {
  print("user added: dev");
  db.users.insert(devUser);
}
