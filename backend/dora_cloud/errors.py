class ServiceError(Exception):
    """A closed, content-free error safe to return to a client."""
    def __init__(self, code, status=400):
        super().__init__(code)
        self.code, self.status = code, status
