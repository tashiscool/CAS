package controllers.cas

import org.slf4j.LoggerFactory

/**
 * Created by tash on 11/23/15.
 */
trait ServicesManager {
  /**
   * Register a service with CAS, or update an existing an entry.
   *
   * @param registeredService the RegisteredService to update or add.
   * @return newly persisted RegisteredService instance
   */
  def save(registeredService: RegisteredService): RegisteredService

  /**
   * Delete the entry for this RegisteredService.
   *
   * @param id the id of the registeredService to delete.
   * @return the registered service that was deleted, null if there was none.
   */
  def delete(id: Long): RegisteredService

  /**
   * Find a RegisteredService by matching with the supplied service.
   *
   * @param service the service to match with.
   * @return the RegisteredService that matches the supplied service.
   */
  def findServiceBy(service: Service): RegisteredService

  /**
   * Find a RegisteredService by matching with the supplied id.
   *
   * @param id the id to match with.
   * @return the RegisteredService that matches the supplied service.
   */
  def findServiceBy(id: Long): RegisteredService

  /**
   * Retrieve the collection of all registered services.
   *
   * @return the collection of all services.
   */
  def getAllServices: Seq[RegisteredService]

  /**
   * Convenience method to let one know if a service exists in the data store.
   *
   * @param service the service to check.
   * @return true if it exists, false otherwise.
   */
  def matchesExistingService(service: Service): Boolean
}

case class DefaultServicesManagerImpl(serviceRegistryDao: ServiceRegistryDao, defaultAttributes: List[String] )  {
  private val LOGGER = LoggerFactory.getLogger(classOf[DefaultServicesManagerImpl] )

  /** Map to store all services. */
  private val services = collection.mutable.Map.empty[Long, RegisteredService]

  def delete (id: Long): RegisteredService = {
    val r: RegisteredService = findServiceBy (id)
    if (r == null) {
      return null
    }
    this.serviceRegistryDao.delete (r)
    this.services.remove (id)
    return r
  }

  /**
   * {@inheritDoc}
   */
  def findServiceBy (service: Service): RegisteredService = {
    this.services.values.find(x => x.matches(service) ).getOrElse(null)
  }

  def findServiceBy (id: Long): RegisteredService = {
    this.services.find{case (x,y) => x == id}.map(_._2).getOrElse(null)
  }


  def matchesExistingService (service: Service): Boolean = {
    return findServiceBy (service) != null
  }

  def save (registeredService: RegisteredService): RegisteredService = {
    val r: RegisteredService = this.serviceRegistryDao.save (registeredService)
    this.services.put (r.getId, r)
    return r
  }

  def reload {
    LOGGER.info ("Reloading registered services.")
    load
  }

  /**
   * Load services that are provided by the DAO.
   */
  private def load {
    val localServices: collection.mutable.Map[Long, RegisteredService] = collection.mutable.Map.empty[Long, RegisteredService]

    import scala.collection.JavaConversions._

    for (r <- this.serviceRegistryDao.load) {
      LOGGER.debug ("Adding registered service {}", r.getServiceId)
      localServices.put (r.getId, r)
    }
    this.services.putAll(localServices)
    LOGGER.info ("Loaded {} services.", this.services.size)
  }
}



