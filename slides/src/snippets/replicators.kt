val auth = BasicAuthenticator(s.userName, s.userPassword.toCharArray())
val endpoint = URLEndpoint(URI(s.publicWsUrl))

val reads = buildReplicator(
    endpoint, auth, ReplicatorType.PULL,
    CollectionConfiguration(db.meals).setPushFilter { doc, _ -> !doc.getBoolean("isTemp") },
)
val writes = buildReplicator(endpoint, auth, ReplicatorType.PUSH_AND_PULL, CollectionConfiguration(db.commands))
