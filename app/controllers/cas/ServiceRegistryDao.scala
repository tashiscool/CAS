package controllers.cas

/**
 * Created by tash on 12/15/15.
 */
trait ServiceRegistryDao {
/**
 * Persist the service in the data store.
 *
 * @param registeredService the service to persist.
 * @return the updated RegisteredService.
 */
def save (registeredService: RegisteredService): RegisteredService

/**
 * Remove the service from the data store.
 *
 * @param registeredService the service to remove.
 * @return true if it was removed, false otherwise.
 */
def delete (registeredService: RegisteredService): Boolean

/**
 * Retrieve the services from the data store.
 *
 * @return the collection of services.
 */
def load: List[RegisteredService]

/**
 * Find service by the numeric id.
 *
 * @param id the id
 * @return the registered service
 */
def findServiceById (id: Long): RegisteredService
}
