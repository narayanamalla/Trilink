import Foundation
import Network

public protocol BonjourDiscoveryDelegate: AnyObject, Sendable {
    func discoveryDidFindPeer(deviceId: String, name: String, endpoint: String)
    func discoveryDidLosePeer(deviceId: String)
}

public final class BonjourDiscovery: @unchecked Sendable {
    public static let serviceType = "_trilink._tcp"
    public static let serviceDomain = "local."

    public weak var delegate: BonjourDiscoveryDelegate?
    private var browser: NWBrowser?
    private let queue = DispatchQueue(label: "com.trilink.discovery")

    public init() {}

    public func startBrowsing() {
        let descriptor = NWBrowser.Descriptor.bonjour(type: Self.serviceType, domain: Self.serviceDomain)
        let parameters = NWParameters()
        let browser = NWBrowser(for: descriptor, using: parameters)
        self.browser = browser

        browser.browseResultsChangedHandler = { [weak self] results, changes in
            guard let self = self else { return }
            for result in results {
                if case .service(let name, _, _, _) = result.endpoint {
                    // Extract endpoint resolution
                    let endpointString = "http://\(name).local:42425"
                    self.delegate?.discoveryDidFindPeer(
                        deviceId: name,
                        name: name,
                        endpoint: endpointString
                    )
                }
            }
        }

        browser.start(queue: queue)
    }

    public func stopBrowsing() {
        browser?.cancel()
        browser = nil
    }
}
