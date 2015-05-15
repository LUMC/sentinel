// dbSetup.js
//
// Script for initial Sentinel MongoDB database setup
//
// Usage: mongo {address}:{port} dbSetup.js

// Don't bootstrap if database already has content
if (db.getCollectionNames().length > 0) {
    throw "ERROR: Sentinel database is already created. Exiting.";
}

print("\n    *** Bootstrapping Sentinel ***\n");

print("    Creating indices for ...");
// fs.files -> index by md5 and metadata.uploader + unique
db.fs.files.createIndex({"md5": 1, "metadata.uploader": 1}, {"unique": true})
print("      - raw uploads");
// annotations -> annotMd5 + unique
db.annotations.createIndex({"annotMd5": 1}, {"unique": true})
print("      - annotation records");
// references -> combinedMd5 + unique
db.references.createIndex({"combinedMd5": 1}, {"unique": true})
print("      - reference records");

print("    Adding the admin user ...");
// NOTE: must be kept in sync with User in the source code
var adminUser = {
    id: "admin",
    email: "admin@sentinel.org",
    isConfirmed: true,
    isAdmin: true,
    creationTime: new Date()
}
db.user.insert(adminUser);

print("\n    ************ Done ************\n");
