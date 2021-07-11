import arrow.core.*
import arrow.core.computations.either
import memeid.UUID

data class User(val email: String, val name: String)
data class ProcessedUser(val id: UUID, val email: String, val name: String)

sealed class UserHandlingException {
    object ProcessingError : UserHandlingException()
    object FetchingError : UserHandlingException()
}
typealias ProcessingError = UserHandlingException.ProcessingError
typealias FetchingError = UserHandlingException.FetchingError

interface Repo {
    suspend fun fetchUsers(): Either<FetchingError, List<User>>
}

fun MockRepo(): Repo = object : Repo {
    override suspend fun fetchUsers(): Either<FetchingError, List<User>> =
        listOf(
            User("simon@arrow-kt.io", "Simon"),
            User("raul@arrow-kt.io", "Raul"),
            // this will cause an error when processed!
            User("jorge[at]arrow-kt.io", "Jorge")
        ).right()
}

interface Persistence {
    suspend fun User.process(): Either<ProcessingError, ProcessedUser>

    suspend fun List<User>.process(): Either<ProcessingError, List<ProcessedUser>> =
        traverseEither {
            it.process()
        }
}

fun MockPersistence(): Persistence = object : Persistence {
    override suspend fun User.process(): Either<ProcessingError, ProcessedUser> =
        if (email.contains(Regex("^(.+)@(.+)$"))) Either.Right(ProcessedUser(UUID.V4.squuid(), email, name))
        else Either.Left(ProcessingError)
}

interface DataLayer : Repo, Persistence

suspend fun DataLayer.getProcessUsers(): Either<UserHandlingException, List<ProcessedUser>> = either {
    val users = fetchUsers().bind()
    val processedUser = users.process().bind()
    processedUser
}

fun DataLayer(persistence: Persistence, repo: Repo): DataLayer =
    object : DataLayer, Repo by repo, Persistence by persistence {}

suspend fun main(): Unit {
    val processedUsers = DataLayer(MockPersistence(), MockRepo()).getProcessUsers()
    processedUsers.fold(
        ifLeft = { when (it) {
            is ProcessingError -> println("Processing Error!")
            is FetchingError -> println("FetchingError Error!")
        }},
        ifRight = ::println
    )
}