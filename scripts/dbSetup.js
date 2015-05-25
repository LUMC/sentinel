// dbSetup.js
//
// Script for initial Sentinel MongoDB database setup for development.
//
// This script creates the required indices and users. Notably,
// it adds two MongoDB users with the following user names:
//
//   - sentinel-owner (with dbOwner role of the sentinel database)
//   - sentinel-api (with readWrite role of the sentinel database)
//
// The sentinel-owner is meant for administering the database, while
// sentinel-api is how the Sentinel API accesses the database. Both accounts
// initially have the same passwords as their usernames. If you intend to
// deploy later, we strongly suggest that these passwords be changed.
//
// The script must be run by an existing MongoDB user that can create
// databases and add users. It will overwrite any existing data for Sentinel,
// so use it carefully.
//
// Usage: mongo {address}:{port} dbSetup.js

print("\n*** Bootstrapping Sentinel ***");

if (db.getCollectionNames().length > 0) {
    print("\nRemoving existing Sentinel setup ...");
    db.dropDatabase();
}

print("\nCreating indices for ...");
// fs.files -> index by md5 and metadata.uploader + unique
db.fs.files.createIndex({"md5": 1, "metadata.userId": 1}, {"unique": true})
print("- raw uploads");
// annotations -> annotMd5 + unique
db.annotations.createIndex({"annotMd5": 1}, {"unique": true})
print("- annotation records");
// references -> combinedMd5 + unique
db.references.createIndex({"combinedMd5": 1}, {"unique": true})
print("- reference records");

print("\nAdding MongoDB users ...");
db.dropUser("sentinel-owner");
db.createUser({
    user: "sentinel-owner",
    pwd: "sentinel-owner",
    roles: [ "dbOwner" ]
});
db.dropUser("sentinel-api");
db.createUser({
    user: "sentinel-api",
    pwd: "sentinel-api",
    roles: [ "readWrite" ]
});

print("\nAdding mock user ...");
// NOTE: must be kept in sync with User in the source code
var devUser = {
    id: "dev",
    email: "dev@sentinel.org",
    // log2 10 hashing round of `dev`
    hashedPassword: "$2a$10$dNNzi9ieIj1Lk/ED184tPOHJeYDCIc/9bvCJUggC8Gl.4d4pEsdn6",
    activeKey: "dev",
    emailVerified: true,
    isAdmin: true,
    creationTime: new Date()
}
db.users.insert(devUser);

print("\n************ Done ************\n");
