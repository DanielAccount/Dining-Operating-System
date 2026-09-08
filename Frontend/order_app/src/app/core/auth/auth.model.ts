export interface LoginRequest {
    email : string;
    password: string;
}

export interface TenantDto {
  id: number;
  businessName: string;
  address: string | null;
  status: string;
}

export interface BranchDto {
  id: number;
  name: string;
  status: string;
}

export interface MeResponse {
  id: number;
  username: string;
  email: string;
  displayName: string;
  preferredLanguage: string;
  active: boolean;
  tenant: TenantDto;
  branch: BranchDto | null;
  role: string;
  permissions: string[];
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: MeResponse;
}