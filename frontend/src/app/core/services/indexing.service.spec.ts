import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { IndexingService } from './indexing.service';
import { IndexRepositoryResponse } from '../models/indexing.model';

describe('IndexingService', () => {
  let service: IndexingService;
  let httpMock: HttpTestingController;

  const response: IndexRepositoryResponse = {
    repositoryRoot: 'E:\\work\\codepilot-ai',
    chunksIndexed: 412,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(IndexingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('POSTs the repositoryRoot to /api/v1/indexing and returns the chunk count', () => {
    let result: IndexRepositoryResponse | undefined;
    service.indexRepository('E:\\work\\codepilot-ai').subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/v1/indexing');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ repositoryRoot: 'E:\\work\\codepilot-ai' });
    req.flush(response);

    expect(result).toEqual(response);
  });

  it('POSTs {repositoryId} (and no path) to /api/v1/indexing when indexing a registered repository', () => {
    let result: IndexRepositoryResponse | undefined;
    service.indexRepositoryById('3f0c2b7e-1111-4222-8333-444455556666').subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/v1/indexing');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ repositoryId: '3f0c2b7e-1111-4222-8333-444455556666' });
    req.flush(response);

    expect(result).toEqual(response);
  });
});
